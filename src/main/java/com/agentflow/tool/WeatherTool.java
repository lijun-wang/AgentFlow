package com.agentflow.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具：通过 wttr.in 免费 API 查询指定城市的天气信�? * HTTP 调用委托�?ExternalApiFetcher，由其提供瞬时故障（超时/5xx）的自动重试
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private final ExternalApiFetcher externalApiFetcher;

    public WeatherTool(ExternalApiFetcher externalApiFetcher) {
        this.externalApiFetcher = externalApiFetcher;
    }

    /**
     * 查询指定城市的天气信�?     *
     * @param city 城市名称（支持中文和英文�?     * @return 天气信息描述
     */
    @Tool(name = "queryWeather", value = "查询指定城市的天气信息，当用户询问某个城市的天气时调用此工具")
    public String queryWeather(@P("要查询天气的城市名称") String city) {
        log.info("调用天气查询工具，城�? {}", city);
        try {
            // 使用 wttr.in �?JSON 格式获取天气数据（瞬时故障由 ExternalApiFetcher 自动重试�?            String response = externalApiFetcher.fetchWeather(city);

            if (response == null || response.isBlank()) {
                return "抱歉，无法获�?" + city + " 的天气信息�?;
            }

            // 解析关键天气信息并返回简洁的描述
            return parseWeatherResponse(city, response);

        } catch (Exception e) {
            log.error("查询天气失败，城�? {}", city, e);
            return "查询 " + city + " 天气失败: " + e.getMessage();
        }
    }

    /**
     * 解析 wttr.in 返回�?JSON 天气数据，提取关键信�?     */
    private String parseWeatherResponse(String city, String jsonResponse) {
        try {
            // 使用简单的字符串解析提取关键天气字�?            // wttr.in 返回�?JSON �?current_condition 包含天气信息
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);

            com.fasterxml.jackson.databind.JsonNode currentCondition = root.path("current_condition").get(0);
            if (currentCondition == null || currentCondition.isMissingNode()) {
                return "抱歉，无法解�?" + city + " 的天气数据�?;
            }

            // 提取关键天气信息
            String tempC = currentCondition.path("temp_C").asText("未知");
            String feelsLikeC = currentCondition.path("FeelsLikeC").asText("未知");
            String humidity = currentCondition.path("humidity").asText("未知");
            String weatherDesc = currentCondition.path("lang_zh").get(0).path("value").asText("未知");
            String windSpeed = currentCondition.path("windspeedKmph").asText("未知");
            String windDir = currentCondition.path("winddir16Point").asText("未知");

            return String.format(
                    "%s 当前天气�?s，温�?%s°C（体�?%s°C），湿度 %s%%，风�?%s km/h�?s方向）�?,
                    city, weatherDesc, tempC, feelsLikeC, humidity, windSpeed, windDir
            );

        } catch (Exception e) {
            log.warn("解析天气 JSON 失败，返回原始摘�?, e);
            // 降级：返回简要提�?            return city + " 天气数据已获取，但解析失败，请稍后重试�?;
        }
    }
}
