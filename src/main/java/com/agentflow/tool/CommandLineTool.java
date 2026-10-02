package com.agentflow.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 本地命令行执行工具：在服务器上执行命令行指令并返回运行结�? * 根据操作系统自动选择 shell（Windows 使用 cmd.exe，其他系统使�?/bin/sh�? * 内置超时控制和输出长度截断，避免长时间阻塞或返回超大文本�?LLM
 */
@Component
public class CommandLineTool {

    private static final Logger log = LoggerFactory.getLogger(CommandLineTool.class);

    /** 命令执行超时时间（秒�?*/
    private static final long TIMEOUT_SECONDS = 60;

    /** 返回�?LLM 的最大输出字符数，超出部分截�?*/
    private static final int MAX_OUTPUT_LENGTH = 4000;

    /** 是否运行�?Windows 系统 */
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * 执行本地命令行指令并返回运行结果
     *
     * @param command       要执行的命令（单条命令字符串�?     * @param workingDirectory 工作目录（可选，为空时使用用户主目录�?     * @return 命令执行结果，包含退出码、标准输出和错误输出
     */
    @Tool(name = "executeCommand", value = "在本地服务器上执行命令行指令并返回运行结果，当用户要求执行命令、运行脚本、查看系统信息或操作本地文件时调用此工具")
    public String executeCommand(
            @P("要执行的命令行指令，例如 dir、ls、ipconfig") String command,
            @P(value = "命令执行的工作目录绝对路径，可为空，为空时使用用户主目录", required = false) String workingDirectory) {
        log.info("调用命令行执行工具，命令: {}，工作目�? {}", command, workingDirectory);

        if (command == null || command.isBlank()) {
            return "命令不能为空，请提供要执行的指令�?;
        }

        try {
            // 根据操作系统选择 shell：Windows 使用 cmd.exe /c，其他系统使�?/bin/sh -c
            ProcessBuilder processBuilder = IS_WINDOWS
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("/bin/sh", "-c", command);

            // 设置工作目录：优先使用用户指定的目录，否则使用用户主目录
            File workDir = (workingDirectory != null && !workingDirectory.isBlank())
                    ? new File(workingDirectory)
                    : new File(System.getProperty("user.home"));
            if (!workDir.isDirectory()) {
                return "工作目录不存�? " + workingDirectory;
            }
            processBuilder.directory(workDir);

            Process process = processBuilder.start();

            // 并行读取标准输出和错误输出，避免缓冲区满导致进程阻塞
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Charset outputCharset = IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset();
            Thread stdoutReader = startStreamReader(process, stdout, outputCharset, false);
            Thread stderrReader = startStreamReader(process, stderr, outputCharset, true);

            // 等待命令完成，超时则强制终止
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                return "命令执行超时（超�?" + TIMEOUT_SECONDS + " 秒），已强制终止。\n部分输出:\n" + truncate(stdout.toString());
            }
            stdoutReader.join(2000);
            stderrReader.join(2000);

            int exitCode = process.exitValue();
            StringBuilder result = new StringBuilder();
            result.append("退出码: ").append(exitCode).append("\n");
            if (stdout.length() > 0) {
                result.append("标准输出:\n").append(truncate(stdout.toString())).append("\n");
            }
            if (stderr.length() > 0) {
                result.append("错误输出:\n").append(truncate(stderr.toString())).append("\n");
            }
            if (stdout.length() == 0 && stderr.length() == 0) {
                result.append("（命令无输出�?);
            }
            return result.toString();

        } catch (Exception e) {
            log.error("命令执行失败: {}", command, e);
            return "命令执行失败: " + e.getMessage();
        }
    }

    /**
     * 启动守护线程读取进程的输出流（标准输出或错误输出�?     *
     * @param process 目标进程
     * @param target  输出内容写入�?StringBuilder
     * @param charset 输出流字符集
     * @param isError true 表示读取错误输出�?     * @return 已启动的读取线程
     */
    private Thread startStreamReader(Process process, StringBuilder target, Charset charset, boolean isError) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(isError ? process.getErrorStream() : process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    target.append(line).append(System.lineSeparator());
                }
            } catch (Exception e) {
                log.warn("读取进程输出流失�?, e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 截断超长输出，避免返回过多内容给 LLM 消�?token
     *
     * @param output 原始输出
     * @return 截断后的输出
     */
    private String truncate(String output) {
        if (output.length() <= MAX_OUTPUT_LENGTH) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_LENGTH) + "\n...（输出过长，已截断，�?" + output.length() + " 字符�?;
    }
}
