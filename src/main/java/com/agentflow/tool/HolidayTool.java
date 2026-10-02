package com.agentflow.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 节假日查询工具：通过外部 API 动态获取中国法定节假日放假安排
 * 优先通过 API 获取最新数据（瞬时故障�?ExternalApiFetcher 自动重试），
 * 重试耗尽仍失败时回退到本地计算（仅包含固定公历节日和清明节）
 */
@Component
public class HolidayTool {

    private static final Logger log = LoggerFactory.getLogger(HolidayTool.class);

    private final ExternalApiFetcher externalApiFetcher;

    /** 节假�?API 地址，可通过配置覆盖（实际请求由 ExternalApiFetcher 发起，此处仅用于日志展示�?*/
    private final String holidayApiBaseUrl;

    public HolidayTool(
            ExternalApiFetcher externalApiFetcher,
            @Value("${holiday.api.base-url:https://timor.tech}") String holidayApiBaseUrl) {
        this.externalApiFetcher = externalApiFetcher;
        this.holidayApiBaseUrl = holidayApiBaseUrl;
    }

    /**
     * 查询指定年份的中国法定节假日安排（优先通过 API 动态获取）
     *
     * @param year 要查询的年份（如 2025�?     * @return 该年份法定节假日的名称、日期和放假天数信息
     */
    @Tool(name = "queryHoliday", value = "查询指定年份的中国法定节假日信息，当用户询问某年的节假日、放假安排时调用此工�?)
    public String queryHoliday(@P("要查询节假日的年份，�?2025") int year) {
        log.info("调用节假日查询工具，年份: {}，API 地址: {}", year, holidayApiBaseUrl);

        // 优先通过 API 获取节假日数据（瞬时故障�?ExternalApiFetcher 自动重试�?        try {
            String response = externalApiFetcher.fetchHoliday(year);

            if (response != null && !response.isBlank()) {
                String parsed = parseApiResponse(year, response);
                if (parsed != null) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.warn("通过 API 获取 {} 年节假日数据失败（含重试），回退到本地计�?, year, e);
        }

        // API 不可用时回退到本地计�?        return calculateFallback(year);
    }

    /**
     * 解析节假�?API 返回�?JSON 数据
     *
     * @return 格式化后的节假日信息，解析失败返�?null
     */
    private String parseApiResponse(int year, String jsonResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);

            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("API 返回�?0 状态码: {}，年�? {}", code, year);
                return null;
            }

            com.fasterxml.jackson.databind.JsonNode holidayMap = root.path("holiday");
            if (holidayMap.isMissingNode() || holidayMap.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(year).append(" 年法定节假日安排：\n\n");

            holidayMap.fields().forEachRemaining(entry -> {
                com.fasterxml.jackson.databind.JsonNode holiday = entry.getValue();
                String name = holiday.path("name").asText("未知");
                String date = holiday.path("date").asText("未知");
                int days = holiday.path("holiday").asInt(0);

                sb.append("�?).append(name).append("�?).append(date);
                if (days > 0) {
                    sb.append("，放�?").append(days).append(" �?);
                }
                sb.append("\n");
            });

            return sb.toString();

        } catch (Exception e) {
            log.warn("解析节假�?API 响应失败，年�? {}", year, e);
            return null;
        }
    }

    /**
     * 本地计算回退方法：API 不可用时计算法定节假�?     * 包含固定公历节日、清明节（天文公式），农历节日需依赖 API 获取精确日期
     */
    private String calculateFallback(int year) {
        StringBuilder sb = new StringBuilder();
        sb.append(year).append(" 年法定节假日（本地计算，农历节日日期为近似值）：\n\n");

        // 元旦：固�?1 �?1 �?        sb.append("【元旦�?�?日，放假 1 天\n");

        // 春节：农历正月初一（需 API 获取精确公历日期�?        sb.append("【春节】农历正月初一（具体公历日期需通过 API 获取）\n");

        // 清明节：天文算法，太阳黄经达 15° �?        int qingming = calculateQingmingDate(year);
        sb.append("【清明节�?�?).append(qingming).append("日，放假 3 天\n");

        // 劳动节：固定 5 �?1 �?        sb.append("【劳动节�?�?日，放假 5 天\n");

        // 端午节：农历五月初五（需 API 获取精确公历日期�?        sb.append("【端午节】农历五月初五（具体公历日期需通过 API 获取）\n");

        // 中秋节：农历八月十五（需 API 获取精确公历日期�?        sb.append("【中秋节】农历八月十五（具体公历日期需通过 API 获取）\n");

        // 国庆节：固定 10 �?1 �?        sb.append("【国庆节�?0�?日，放假 7 天\n");

        sb.append("\n注：春节、端午节、中秋节为农历节日，每年公历日期不同�?);
        sb.append("请确保节假日 API 可用以获取精确放假安排�?);

        return sb.toString();
    }

    /**
     * 计算清明节的公历日期（基于天文公式）
     * 清明节是太阳黄经�?15° 时的节气，通常�?4 �?4 日或 5 �?     *
     * @return 清明节在 4 月的日期�? �?5�?     */
    private int calculateQingmingDate(int year) {
        // 世纪常数公式：[Y*D+C]-L
        // 21 世纪 C=4.81，D=0.2422，L=Y/4 取整（忽略被 100 整除不被 400 整除的年份）
        int y = year % 100;
        int l = y / 4;
        int qm = (int) (y * 0.2422 + 4.81) - l;
        return qm;
    }
}
