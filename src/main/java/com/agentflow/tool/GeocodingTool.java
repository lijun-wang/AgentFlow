package com.agentflow.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 城市经纬度查询工具：通过 Open-Meteo 免费地理编码 API 查询指定城市的经纬度
 * HTTP 调用委托�?ExternalApiFetcher，由其提供瞬时故障（超时/5xx）的自动重试
 */
@Component
public class GeocodingTool {

    private static final Logger log = LoggerFactory.getLogger(GeocodingTool.class);

    private final ExternalApiFetcher externalApiFetcher;

    public GeocodingTool(ExternalApiFetcher externalApiFetcher) {
        this.externalApiFetcher = externalApiFetcher;
    }

    /**
     * 查询指定城市的经纬度坐标
     *
     * @param city 城市名称（支持中文和英文�?     * @return 包含经纬度信息的描述文本
     */
    @Tool(name = "queryCityCoordinates", value = "查询指定城市的经纬度坐标，当需要获取某个城市的地理位置坐标（经度、纬度）时调用此工具")
    public String queryCityCoordinates(@P("要查询经纬度的城市名�?) String city) {
        log.info("调用经纬度查询工具，城市: {}", city);
        try {
            // 请求 Open-Meteo 地理编码数据（瞬时故障由 ExternalApiFetcher 自动重试�?            String response = externalApiFetcher.fetchGeocoding(city);

            if (response == null || response.isBlank()) {
                return "抱歉，无法获�?" + city + " 的经纬度信息�?;
            }

            return parseGeocodingResponse(city, response);

        } catch (Exception e) {
            log.error("查询经纬度失败，城市: {}", city, e);
            return "查询 " + city + " 经纬度失�? " + e.getMessage();
        }
    }

    /**
     * 解析 Open-Meteo 返回的地理编�?JSON 数据
     */
    private String parseGeocodingResponse(String city, String jsonResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);

            com.fasterxml.jackson.databind.JsonNode results = root.path("results");
            if (results.isMissingNode() || results.isEmpty()) {
                return "未找到城�?\"" + city + "\" 的地理坐标，请检查城市名称是否正确�?;
            }

            com.fasterxml.jackson.databind.JsonNode first = results.get(0);
            String name = first.path("name").asText(city);
            double latitude = first.path("latitude").asDouble();
            double longitude = first.path("longitude").asDouble();
            String country = first.path("country").asText("");
            String admin1 = first.path("admin1").asText("");
            int population = first.path("population").asInt(0);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s 的坐标：纬度 %.4f°，经�?%.4f°�?, name, latitude, longitude));
            if (!country.isEmpty()) {
                sb.append(" 所属国家：").append(country).append("�?);
            }
            if (!admin1.isEmpty()) {
                sb.append(" 所属省�?州：").append(admin1).append("�?);
            }
            if (population > 0) {
                sb.append(" 人口�?").append(String.format("%,d", population)).append(" 人�?);
            }

            return sb.toString();

        } catch (Exception e) {
            log.warn("解析地理编码 JSON 失败", e);
            return city + " 的经纬度数据已获取，但解析失败，请稍后重试�?;
        }
    }
}
