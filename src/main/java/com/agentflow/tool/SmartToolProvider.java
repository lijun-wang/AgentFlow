package com.agentflow.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;

/**
 * 智能工具提供者：根据用户消息内容按需加载工具，避免每次请求都携带全部工具描述
 * 通过关键词匹配判断用户意图，仅向 LLM 提供相关工具，减�?token 消�? * 当无法判断意图时，回退到提供全部工�? */
public class SmartToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SmartToolProvider.class);

    /**
     * 工具注册项：包含工具规格、执行器和匹配关键词
     */
    private record ToolEntry(ToolSpecification specification, DefaultToolExecutor executor, String[] keywords) {
    }

    private final List<ToolEntry> toolEntries = new ArrayList<>();

    /**
     * 构造函数：注册所有可用工具及其匹配关键词
     *
     * @param weatherTool     天气查询工具
     * @param geocodingTool   经纬度查询工�?     * @param holidayTool     节假日查询工�?     * @param commandLineTool 本地命令行执行工�?     */
    public SmartToolProvider(Object weatherTool, Object geocodingTool, Object holidayTool, Object commandLineTool) {
        registerTool(weatherTool, "天气", "weather", "温度", "气温", "下雨", "下雪", "风力", "湿度", "�?, "�?, "�?, "�?);
        registerTool(geocodingTool, "经纬�?, "坐标", "位置", "经度", "纬度", "地理位置", "地理坐标", "geocoding", "coordinates");
        registerTool(holidayTool, "节假�?, "放假", "节日", "holiday", "春节", "国庆", "劳动�?, "端午", "中秋", "元旦", "清明", "放假安排", "假期");
        registerTool(commandLineTool, "命令", "命令�?, "指令", "执行", "运行", "脚本", "shell", "cmd", "command", "终端", "terminal", "dir", "ls", "ipconfig");
    }

    /**
     * 注册单个工具：提�?@Tool 注解的方法规格，创建执行器，绑定关键�?     *
     * @param toolObject 工具对象实例
     * @param keywords   用于意图匹配的关键词列表
     */
    private void registerTool(Object toolObject, String... keywords) {
        List<ToolSpecification> specs = toolSpecificationsFrom(toolObject);
        for (ToolSpecification spec : specs) {
            Method method = findToolMethod(toolObject, spec.name());
            if (method != null) {
                DefaultToolExecutor executor = new DefaultToolExecutor(toolObject, method);
                toolEntries.add(new ToolEntry(spec, executor, keywords));
                log.info("注册工具: {}，关键词: {}", spec.name(), String.join(", ", keywords));
            }
        }
    }

    /**
     * 在工具对象中查找指定名称�?@Tool 注解方法
     *
     * @param toolObject 工具对象
     * @param toolName   工具名称（@Tool 注解�?name 属性）
     * @return 匹配的方法，未找到返�?null
     */
    private Method findToolMethod(Object toolObject, String toolName) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                String name = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
                if (name.equals(toolName)) {
                    return method;
                }
            }
        }
        log.warn("未找到工具方�? {}", toolName);
        return null;
    }

    /**
     * 根据用户消息内容动态提供工具：分析消息中的关键词，仅返回匹配的工具
     * 当无关键词匹配时回退到提供全部工具，确保不遗漏用户意�?     *
     * @param request 包含用户消息的请求对�?     * @return 按需筛选后的工具列�?     */
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        String userMessage = request.userMessage().singleText().toLowerCase();
        log.info("分析用户消息，按需加载工具: {}", userMessage);

        // 根据关键词匹配工�?        Map<String, ToolEntry> matchedTools = new LinkedHashMap<>();
        for (ToolEntry entry : toolEntries) {
            for (String keyword : entry.keywords()) {
                if (userMessage.contains(keyword.toLowerCase())) {
                    matchedTools.put(entry.specification().name(), entry);
                    log.info("消息匹配工具: {}（关键词: {}�?, entry.specification().name(), keyword);
                    break;
                }
            }
        }

        // 构建结果：有匹配则返回匹配的工具，无匹配则回退到全部工�?        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        if (matchedTools.isEmpty()) {
            log.info("未匹配到特定工具，提供全�?{} 个工�?, toolEntries.size());
            for (ToolEntry entry : toolEntries) {
                builder.add(entry.specification(), entry.executor());
            }
        } else {
            log.info("按需加载 {} 个工�? {}", matchedTools.size(), matchedTools.keySet());
            for (ToolEntry entry : matchedTools.values()) {
                builder.add(entry.specification(), entry.executor());
            }
        }

        return builder.build();
    }

    /**
     * 标记为动态提供者：在工具调用循环中每次 LLM 调用前重新评估工具列�?     * 确保多轮工具调用场景下也能按需提供正确的工�?     */
    @Override
    public boolean isDynamic() {
        return true;
    }
}
