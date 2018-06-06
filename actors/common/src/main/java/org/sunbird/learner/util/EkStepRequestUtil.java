/** */
package org.sunbird.learner.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.RestUtil;
import org.sunbird.common.responsecode.ResponseCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.BaseRequest;

import akka.dispatch.ExecutionContexts;
import akka.dispatch.Mapper;
import scala.concurrent.Future;

/**
 * This class will make the call to EkStep content search
 *
 * @author Manzarul
 */
public final class EkStepRequestUtil {

  private static ObjectMapper mapper = new ObjectMapper();

  private EkStepRequestUtil() {}
  
  private static String contentSearchURL = RestUtil.getBasePath() + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL); 
  
  
  public static Future<Map<String, Object>> searchContentAsync(String body) throws Exception {
	  BaseRequest request = Unirest.post(contentSearchURL).body(body);
	  Future<JsonNode> response = RestUtil.executeAsync(request);
	  return response.map(new Mapper<JsonNode, Map<String, Object>>() {

		@Override
		public Map<String, Object> apply(JsonNode jsonNode) {
			try {
				JSONObject result =jsonNode.getObject().getJSONObject("result");
				Map<String, Object> resultMap = jsonToMap(result);
				  Object contents = resultMap.get(JsonKey.CONTENT);
				  resultMap.remove(JsonKey.CONTENT);
				  resultMap.put(JsonKey.CONTENTS, contents);
				  String resmsgId = null;
			      String apiId = null;
			      Map<String, Object> param = new HashMap<>();
			      param.put(JsonKey.RES_MSG_ID, resmsgId);
			      param.put(JsonKey.API_ID, apiId);
			      resultMap.put(JsonKey.PARAMS, param);
				  return resultMap;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}
		  
	}, ExecutionContexts.global());
  }
  
  public static Map<String, Object> searchContent(String body) throws Exception {
	  BaseRequest request = Unirest.post(contentSearchURL).body(body);
	  HttpResponse<JsonNode> response = RestUtil.execute(request);
	  if (RestUtil.isSuccessful(response)) {
		  JSONObject result = response.getBody().getObject().getJSONObject("result");
		  Map<String, Object> resultMap = jsonToMap(result);
		  Object contents = resultMap.get(JsonKey.CONTENT);
		  resultMap.remove(JsonKey.CONTENT);
		  resultMap.put(JsonKey.CONTENTS, contents);
		  String resmsgId = RestUtil.getFromResponse(response, "params.resmsgid");
	      String apiId = RestUtil.getFromResponse(response, "id");
	      Map<String, Object> param = new HashMap<>();
	      param.put(JsonKey.RES_MSG_ID, resmsgId);
	      param.put(JsonKey.API_ID, apiId);
	      resultMap.put(JsonKey.PARAMS, param);
		  return resultMap;
	  } else {
		  String err = RestUtil.getFromResponse(response, "params.err");
          String message = RestUtil.getFromResponse(response, "params.errmsg");
          throw new ProjectCommonException(err, message, ResponseCode.SERVER_ERROR.getResponseCode());
	  }
  }
  
  public static Map<String, Object> jsonToMap(JSONObject object) throws JSONException {
	    Map<String, Object> map = new HashMap<String, Object>();
	 
	    Iterator<String> keysItr = object.keys();
	    while(keysItr.hasNext()) {
	        String key = keysItr.next();
	        Object value = object.get(key);
	 
	        if(value instanceof JSONArray) {
	            value = toList((JSONArray) value);
	        }
	 
	        else if(value instanceof JSONObject) {
	            value = jsonToMap((JSONObject) value);
	        }
	        map.put(key, value);
	    }
	    return map;
	}
  
  public static List<Object> toList(JSONArray array) throws JSONException {
	    List<Object> list = new ArrayList<Object>();
	    for(int i = 0; i < array.length(); i++) {
	        Object value = array.get(i);
	        if(value instanceof JSONArray) {
	            value = toList((JSONArray) value);
	        }
	 
	        else if(value instanceof JSONObject) {
	            value = jsonToMap((JSONObject) value);
	        }
	        list.add(value);
	    }
	    return list;
	}

  /**
   * @param params String
   * @param headers Map<String, String>
   * @return Map<String,Object>
   */
  public static Map<String, Object> searchContent(String params, Map<String, String> headers) {
    Map<String, Object> resMap = new HashMap<>();
    String response = "";
    JSONObject jObject;
    try {
      String baseSearchUrl = System.getenv(JsonKey.EKSTEP_BASE_URL);
      if (StringUtils.isBlank(baseSearchUrl)) {
        baseSearchUrl = PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_BASE_URL);
      }
      headers.put(
          JsonKey.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.EKSTEP_AUTHORIZATION));
      headers.put("Content-Type", "application/json");
      if (StringUtils.isBlank(headers.get(JsonKey.AUTHORIZATION))) {
        headers.put(
            JsonKey.AUTHORIZATION,
            PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION));
      }
      ProjectLogger.log("making call for content search ==" + params, LoggerEnum.INFO.name());
      response =
          HttpUtil.sendPostRequest(
              baseSearchUrl
                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
              params,
              headers);
      jObject = new JSONObject(response);
      String resmsgId = (String) jObject.getJSONObject("params").get("resmsgid");
      String apiId = jObject.getString("id");
      String resultStr = jObject.getString(JsonKey.RESULT);
      Map<String, Object> data = mapper.readValue(resultStr, Map.class);
      ProjectLogger.log(
          "Total number of content fetched from Ekstep while assembling page data : "
              + data.get("count"),
          LoggerEnum.INFO.name());
      Object contentList = data.get(JsonKey.CONTENT);
      Map<String, Object> param = new HashMap<>();
      param.put(JsonKey.RES_MSG_ID, resmsgId);
      param.put(JsonKey.API_ID, apiId);
      resMap.put(JsonKey.PARAMS, param);
      resMap.put(JsonKey.CONTENTS, contentList);
      Iterator<Map.Entry<String, Object>> itr = data.entrySet().iterator();
      while (itr.hasNext()) {
        Map.Entry<String, Object> entry = itr.next();
        if (!JsonKey.CONTENT.equals(entry.getKey())) {
          resMap.put(entry.getKey(), entry.getValue());
        }
      }
    } catch (IOException | JSONException e) {
      ProjectLogger.log("Error found during contnet search parse==" + e.getMessage(), e);
    }
    return resMap;
  }
}
