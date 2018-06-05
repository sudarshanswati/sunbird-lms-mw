package org.sunbird.learner.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.EkStepRequestUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.telemetry.util.TelemetryUtil;

/**
 * This actor will handle page management operation .
 *
 * @author Amit Kumar
 */
@ActorConfig(
  tasks = {
    "createPage",
    "updatePage",
    "getPageData",
    "getPageSettings",
    "getPageSetting",
    "createSection",
    "updateSection",
    "getSection",
    "getAllSection"
  },
  asyncTasks = {}
)
public class PageManagementActor extends BaseActor {

  private Util.DbInfo pageDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_MGMT_DB);
  private Util.DbInfo sectionDbInfo = Util.dbInfoMap.get(JsonKey.SECTION_MGMT_DB);
  private Util.DbInfo pageSectionDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_SECTION_DB);
  private Util.DbInfo orgDbInfo = Util.dbInfoMap.get(JsonKey.ORG_DB);
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, JsonKey.PAGE);
    // set request id fto thread loacl...
    ExecutionContext.setRequestId(request.getRequestId());
    if (request.getOperation().equalsIgnoreCase(ActorOperations.CREATE_PAGE.getValue())) {
      createPage(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_PAGE.getValue())) {
      updatePage(request);
    } else if (request
        .getOperation()
        .equalsIgnoreCase(ActorOperations.GET_PAGE_SETTING.getValue())) {
      getPageSetting(request);
    } else if (request
        .getOperation()
        .equalsIgnoreCase(ActorOperations.GET_PAGE_SETTINGS.getValue())) {
      getPageSettings();
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_PAGE_DATA.getValue())) {
      getPageData(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.CREATE_SECTION.getValue())) {
      createPageSection(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_SECTION.getValue())) {
      updatePageSection(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_SECTION.getValue())) {
      getSection(request);
    } else if (request
        .getOperation()
        .equalsIgnoreCase(ActorOperations.GET_ALL_SECTION.getValue())) {
      getAllSections();
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void getAllSections() {
    Response response = null;
    response =
        cassandraOperation.getAllRecords(sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    for (Map<String, Object> map : result) {
      removeUnwantedData(map, "");
    }
    Response sectionMap = new Response();
    sectionMap.put(JsonKey.SECTIONS, response.get(JsonKey.RESPONSE));
    sender().tell(response, self());
  }

  private void getSection(Request actorMessage) {
    Response response = null;
    Map<String, Object> req = actorMessage.getRequest();
    String sectionId = (String) req.get(JsonKey.ID);
    response =
        cassandraOperation.getRecordById(
            sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (!(result.isEmpty())) {
      Map<String, Object> map = result.get(0);
      removeUnwantedData(map, "");
      Response section = new Response();
      section.put(JsonKey.SECTION, response.get(JsonKey.RESPONSE));
    }
    sender().tell(response, self());
  }

  private void updatePageSection(Request actorMessage) {
    ProjectLogger.log("Inside updatePageSection method", LoggerEnum.INFO);
    Map<String, Object> req = actorMessage.getRequest();
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(
            JsonKey.SEARCH_QUERY, mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        ProjectLogger.log("Exception occurred while processing search query " + e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(
            JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        ProjectLogger.log("Exception occurred while processing display " + e.getMessage(), e);
      }
    }
    sectionMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
    ProjectLogger.log("update section details", LoggerEnum.INFO);
    Response response =
        cassandraOperation.updateRecord(
            sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionMap);
    sender().tell(response, self());
    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) sectionMap.get(JsonKey.ID), JsonKey.PAGE_SECTION, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject);
    // update DataCacheHandler section map with updated page section data
    ProjectLogger.log("Calling  updateSectionDataCache method", LoggerEnum.INFO);
    updateSectionDataCache(response, sectionMap);
  }

  private void createPageSection(Request actorMessage) {
    ProjectLogger.log("Inside createPageSection method", LoggerEnum.INFO);
    Map<String, Object> req = actorMessage.getRequest();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(
            JsonKey.SEARCH_QUERY, mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        ProjectLogger.log("Exception occurred while processing search Query " + e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(
            JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(
                "Exception occurred while processing search Query "
                    + sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    sectionMap.put(JsonKey.ID, uniqueId);
    sectionMap.put(JsonKey.STATUS, ProjectUtil.Status.ACTIVE.getValue());
    sectionMap.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
    Response response =
        cassandraOperation.insertRecord(
            sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionMap);
    response.put(JsonKey.SECTION_ID, uniqueId);
    sender().tell(response, self());
    targetObject =
        TelemetryUtil.generateTargetObject(uniqueId, JsonKey.PAGE_SECTION, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject);
    // update DataCacheHandler section map with new page section data
    ProjectLogger.log("Calling  updateSectionDataCache method", LoggerEnum.INFO);
    updateSectionDataCache(response, sectionMap);
  }

  private void updateSectionDataCache(Response response, Map<String, Object> sectionMap) {
    new Thread(
            () -> {
              if ((JsonKey.SUCCESS).equalsIgnoreCase((String) response.get(JsonKey.RESPONSE))) {
                DataCacheHandler.getSectionMap()
                    .put((String) sectionMap.get(JsonKey.ID), sectionMap);
              }
            })
        .start();
  }

  @SuppressWarnings("unchecked")
  private void getPageData(Request actorMessage) throws Exception {
    ProjectLogger.log("Inside getPageData method", LoggerEnum.INFO);
    String sectionQuery = null;
    List<Map<String, Object>> sectionList = new ArrayList<>();
    Map<String, Object> filterMap = new HashMap<>();
    Map<String, Object> req = (Map<String, Object>) actorMessage.getRequest().get(JsonKey.PAGE);
    String pageName = (String) req.get(JsonKey.PAGE_NAME);
    String source = (String) req.get(JsonKey.SOURCE);
    String orgId = (String) req.get(JsonKey.ORGANISATION_ID);
    filterMap.putAll(req);
    filterMap.remove(JsonKey.PAGE_NAME);
    filterMap.remove(JsonKey.SOURCE);
    filterMap.remove(JsonKey.ORG_CODE);
    filterMap.remove(JsonKey.FILTERS);
    filterMap.remove(JsonKey.CREATED_BY);
    Map<String, Object> reqFilters = (Map<String, Object>) req.get(JsonKey.FILTERS);

    /** if orgId is not then consider default page */
    if (StringUtils.isBlank(orgId)) {
      orgId = "NA";
    }
//    ProjectLogger.log("Fetching data from Cache for " + orgId + ":" + pageName, LoggerEnum.INFO);
    Map<String, Object> pageMap = DataCacheHandler.getPageMap().get(orgId + ":" + pageName);
    if (null == pageMap) {
      throw new ProjectCommonException(
          ResponseCode.pageDoesNotExist.getErrorCode(),
          ResponseCode.pageDoesNotExist.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    if (StringUtils.equalsIgnoreCase(ProjectUtil.Source.WEB.getValue(), source) && StringUtils.isNotBlank((String) pageMap.get(JsonKey.PORTAL_MAP))) {
        sectionQuery = (String) pageMap.get(JsonKey.PORTAL_MAP);
    } else {
      if (StringUtils.isNotBlank((String) pageMap.get(JsonKey.APP_MAP))) {
        sectionQuery = (String) pageMap.get(JsonKey.APP_MAP);
      }
    }
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> responseMap = new HashMap<>();

    Object[] arr = mapper.readValue(sectionQuery, Object[].class);

    for (Object obj : arr) {
    		Map<String, Object> sectionMap = (Map<String, Object>) obj;
    		Map<String, Object> sectionData = new HashMap<>(DataCacheHandler.getSectionMap().get(sectionMap.get(JsonKey.ID)));
    		getContentData(sectionData, reqFilters, filterMap);
    		sectionData.put(JsonKey.GROUP, sectionMap.get(JsonKey.GROUP));
    		sectionData.put(JsonKey.INDEX, sectionMap.get(JsonKey.INDEX));
    		removeUnwantedData(sectionData, "getPageData");
    		sectionList.add(sectionData);
    }

    responseMap.put(JsonKey.NAME, pageMap.get(JsonKey.NAME));
    responseMap.put(JsonKey.ID, pageMap.get(JsonKey.ID));
    responseMap.put(JsonKey.SECTIONS, sectionList);
    Response pageResponse = new Response();
    pageResponse.put(JsonKey.RESPONSE, responseMap);
    sender().tell(pageResponse, self());
  }

  @SuppressWarnings("unchecked")
  private void getPageSetting(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    String pageName = (String) req.get(JsonKey.ID);
    Response response =
        cassandraOperation.getRecordsByProperty(
            pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), JsonKey.PAGE_NAME, pageName);
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (!(result.isEmpty())) {
      Map<String, Object> pageDO = result.get(0);
      Map<String, Object> responseMap = getPageSetting(pageDO);
      response.getResult().put(JsonKey.PAGE, responseMap);
      response.getResult().remove(JsonKey.RESPONSE);
    }
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void getPageSettings() {
    Response response =
        cassandraOperation.getAllRecords(pageDbInfo.getKeySpace(), pageDbInfo.getTableName());
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    List<Map<String, Object>> pageList = new ArrayList<>();
    for (Map<String, Object> pageDO : result) {
      Map<String, Object> responseMap = getPageSetting(pageDO);
      pageList.add(responseMap);
    }
    response.getResult().put(JsonKey.PAGE, pageList);
    response.getResult().remove(JsonKey.RESPONSE);
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void updatePage(Request actorMessage) {
    ProjectLogger.log("Inside updatePage method", LoggerEnum.INFO);
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    if (StringUtils.isBlank((String) pageMap.get(JsonKey.ORGANISATION_ID))) {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    if (!StringUtils.isBlank((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res =
          cassandraOperation.getRecordsByProperties(
              pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        Map<String, Object> page = ((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).get(0);
        if (!(((String) page.get(JsonKey.ID)).equals(pageMap.get(JsonKey.ID)))) {
          ProjectCommonException exception =
              new ProjectCommonException(
                  ResponseCode.pageAlreadyExist.getErrorCode(),
                  ResponseCode.pageAlreadyExist.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
          return;
        }
      }
    }
    pageMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        ProjectLogger.log("Exception occurred while updating portal map data " + e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        ProjectLogger.log("Exception occurred while updating app map data " + e.getMessage(), e);
      }
    }
    Response response =
        cassandraOperation.updateRecord(
            pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), pageMap);
    sender().tell(response, self());

    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) pageMap.get(JsonKey.ID), JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject);
    // update DataCacheHandler page map with updated page data
    ProjectLogger.log(
        "Calling updatePageDataCacheHandler while updating page data ", LoggerEnum.INFO);
    updatePageDataCacheHandler(response, pageMap);
  }

  @SuppressWarnings("unchecked")
  private void createPage(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    String orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
    if (StringUtils.isNotBlank(orgId)) {
      validateOrg(orgId);
    } else {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (!StringUtils.isBlank((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res =
          cassandraOperation.getRecordsByProperties(
              pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.pageAlreadyExist.getErrorCode(),
                ResponseCode.pageAlreadyExist.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }
    }
    pageMap.put(JsonKey.ID, uniqueId);
    pageMap.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    Response response =
        cassandraOperation.insertRecord(
            pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), pageMap);
    response.put(JsonKey.PAGE_ID, uniqueId);
    sender().tell(response, self());
    targetObject = TelemetryUtil.generateTargetObject(uniqueId, JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject);

    updatePageDataCacheHandler(response, pageMap);
  }

  private void updatePageDataCacheHandler(Response response, Map<String, Object> pageMap) {
    // update DataCacheHandler page map with new page data
    new Thread(
            () -> {
              if (JsonKey.SUCCESS.equalsIgnoreCase((String) response.get(JsonKey.RESPONSE))) {
                String orgId = "NA";
                if (pageMap.containsKey(JsonKey.ORGANISATION_ID)) {
                  orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
                }
                DataCacheHandler.getPageMap()
                    .put(orgId + ":" + (String) pageMap.get(JsonKey.PAGE_NAME), pageMap);
              }
            })
        .start();
  }

  @SuppressWarnings("unchecked")
  private void getContentData(
      Map<String, Object> section,
      Map<String, Object> reqFilters,
      Map<String, Object> filterMap) throws Exception {
//    Map<String, Object> map = new HashMap<>();
//    try {
//      map = mapper.readValue((String) section.get(JsonKey.SEARCH_QUERY), HashMap.class);
//    } catch (IOException e) {
//      ProjectLogger.log(e.getMessage(), e);
//    }
//    Set<Entry<String, Object>> filterEntrySet = filterMap.entrySet();
//    for (Entry<String, Object> entry : filterEntrySet) {
//      if (!entry.getKey().equalsIgnoreCase(JsonKey.FILTERS)) {
//        ((Map<String, Object>) map.get(JsonKey.REQUEST)).put(entry.getKey(), entry.getValue());
//      }
//    }
//    Map<String, Object> filters =
//        (Map<String, Object>) ((Map<String, Object>) map.get(JsonKey.REQUEST)).get(JsonKey.FILTERS);
//    ProjectLogger.log(
//        "default search query for ekstep for page data assemble api : "
//            + (String) section.get(JsonKey.SEARCH_QUERY),
//        LoggerEnum.INFO);
//    applyFilters(filters, reqFilters);
//    String query = "";

//    try {
//      query = mapper.writeValueAsString(map);
//    } catch (Exception e) {
//      ProjectLogger.log("Exception occurred while parsing filters for Ekstep search query", e);
//    }
//    if (StringUtils.isBlank(query)) {
//      query = (String) section.get(JsonKey.SEARCH_QUERY);
//    }
//    ProjectLogger.log(
//        "search query after applying filter for ekstep for page data assemble api : " + query,
//        LoggerEnum.INFO);
    String resStr = "{\"contents\":[{\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112470675618004992181/29-course_1524154287679_do_112470675618004992181_3.0_spine.ecar\",\"channel\":\"0124858228063600641\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112470675618004992181/29-course_1524154287679_do_112470675618004992181_3.0_spine.ecar\",\"size\":85807.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112470675618004992181/artifact/1ef4769e36c4d18cfd9832cd7cb5d03e_1475774424986.thumb.jpeg\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_112470676011761664182\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/pdf\\\":1,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"Ekstep\",\"identifier\":\"do_112470675618004992181\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112470675618004992181/artifact/do_112470675618004992181toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",\"childNodes\":[\"do_112470102008537088160\",\"do_112470676011761664182\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"c_mybatch_course_count\":0,\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"Ekstep\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Draft\",\"size\":85807.0,\"lastPublishedOn\":\"2018-04-19T16:11:27.671+0000\",\"name\":\"29 course\",\"publisher\":\"EkStep\",\"status\":\"Live\",\"code\":\"org.sunbird.hcDa0B\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/1ef4769e36c4d18cfd9832cd7cb5d03e_1475774424986.jpeg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-19T16:11:16.466+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-19T16:11:27.274+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-26T00:00:04.006+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":3.0,\"versionKey\":\"1524154287601\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112470675618004992181/29-course_1524154287679_do_112470675618004992181_3.0_spine.ecar\",\"dialcodes\":[\"61U24C\"],\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-03-29T04:56:00.656+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":1,\"compatibilityLevel\":4,\"c_Sunbird_Dev_private_batch_count\":0,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":34265},{\"subject\":\"Mathematics\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131909441945601309/aaaa_1527489220507_do_1125131909441945601309_1.0_spine.ecar\",\"organisation\":[\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131909441945601309/aaaa_1527489220507_do_1125131909441945601309_1.0_spine.ecar\",\"size\":263122.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"Class 1\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131909441945601309/artifact/short_stories_lionandmouse3_1467102846349.thumb.jpg\",\"children\":[],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":2,\\\"application/vnd.ekstep.content-collection\\\":3,\\\"application/vnd.ekstep.ecml-archive\\\":2}\",\"contentType\":\"Course\",\"identifier\":\"do_1125131909441945601309\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\",\"Appropriate Title, Description\",\"Correct Board, Grade, Subject, Medium\",\"Appropriate tags such as Resource Type, Concepts\",\"Relevant Keywords\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131909441945601309/artifact/do_1125131909441945601309toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":2,\\\"Collection\\\":2,\\\"Story\\\":2}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125012388684431361198\",\"LP_NFTT_1735640\",\"LP_NFT_Collection_1552869\",\"do_1125131911304970241310\",\"do_1125110622654464001294\",\"LP_NFTT_1982433\",\"do_1125012406331719681200\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":263122.0,\"lastPublishedOn\":\"2018-05-28T06:33:40.349+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"AAAA\",\"status\":\"Live\",\"code\":\"org.sunbird.4oK7Ke\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/short_stories_lionandmouse3_1467102846349.jpg\",\"createdOn\":\"2018-05-28T06:32:08.929+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-28T06:33:39.518+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-28T08:00:01.343+0000\",\"createdFor\":[\"ORG_001\"],\"creator\":\"sunbird Test\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527489219518\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125131909441945601309/aaaa_1527489220507_do_1125131909441945601309_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-28T06:32:54.557+0000\",\"createdBy\":\"63b0870c-f370-4f96-842d-f6a7fa2db1df\",\"compatibilityLevel\":4,\"leafNodesCount\":4,\"c_Sunbird_Dev_private_batch_count\":1,\"IL_UNIQUE_ID\":\"do_1125131909441945601309\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":73218},{\"subject\":\"Mathematics\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131878408273921307/abc-testing_1527489056077_do_1125131878408273921307_1.0_spine.ecar\",\"organisation\":[\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131878408273921307/abc-testing_1527489056077_do_1125131878408273921307_1.0_spine.ecar\",\"size\":293562.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131878408273921307/artifact/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.thumb.jpeg\",\"children\":[],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":2,\\\"application/vnd.ekstep.content-collection\\\":3,\\\"application/vnd.ekstep.ecml-archive\\\":3}\",\"contentType\":\"Course\",\"identifier\":\"do_1125131878408273921307\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\",\"Appropriate Title, Description\",\"Correct Board, Grade, Subject, Medium\",\"Appropriate tags such as Resource Type, Concepts\",\"Relevant Keywords\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131878408273921307/artifact/do_1125131878408273921307toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":2,\\\"Collection\\\":3,\\\"Story\\\":2}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125012388684431361198\",\"LP_NFTT_1735640\",\"do_1125131881347563521308\",\"LP_NFT_Collection_1552869\",\"do_11228252518349209612\",\"do_1125110622654464001294\",\"LP_NFTT_1982433\",\"do_1125012406331719681200\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":293562.0,\"lastPublishedOn\":\"2018-05-28T06:30:56.047+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"ABC TESTing\",\"status\":\"Live\",\"code\":\"org.sunbird.A2JMdd\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.jpeg\",\"createdOn\":\"2018-05-28T06:25:50.100+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-28T06:30:55.254+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-28T06:30:56.558+0000\",\"createdFor\":[\"ORG_001\"],\"creator\":\"Vaishnavi Manjunath\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527489055254\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125131878408273921307/abc-testing_1527489056077_do_1125131878408273921307_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-28T06:26:48.853+0000\",\"createdBy\":\"799a2d87-5741-485c-a25c-b844ce4ad73d\",\"compatibilityLevel\":4,\"leafNodesCount\":5,\"IL_UNIQUE_ID\":\"do_1125131878408273921307\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":79096},{\"subject\":\"English\",\"channel\":\"505c7c48ac6dc1edc9b08f21db5a571d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125132098566553601315/content-by-harish_1527491685657_do_1125132098566553601315_1.0_spine.ecar\",\"organisation\":[\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125132098566553601315/content-by-harish_1527491685657_do_1125132098566553601315_1.0_spine.ecar\",\"size\":73234.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125132098566553601315/artifact/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.thumb.jpeg\",\"children\":[],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_1125132098566553601315\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\",\"Relevant Keywords\",\"Appropriate tags such as Resource Type, Concepts\",\"Correct Board, Grade, Subject, Medium\",\"Appropriate Title, Description\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125132098566553601315/artifact/do_1125132098566553601315toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125132099846471681316\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":73234.0,\"lastPublishedOn\":\"2018-05-28T07:14:45.571+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Content by Harish\",\"status\":\"Live\",\"code\":\"org.sunbird.hyfwzA\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.jpeg\",\"createdOn\":\"2018-05-28T07:10:37.580+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-28T07:14:44.918+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-28T07:14:46.035+0000\",\"createdFor\":[\"ORG_001\"],\"creator\":\"N. T. RAO . creator_org_001\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527491684918\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125132098566553601315/content-by-harish_1527491685657_do_1125132098566553601315_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-28T07:12:08.912+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"compatibilityLevel\":4,\"leafNodesCount\":0,\"IL_UNIQUE_ID\":\"do_1125132098566553601315\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":16782},{\"subject\":\"Mathematics\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112514571651391488174/content-for-manzarul_1527658061566_do_112514571651391488174_1.0_spine.ecar\",\"organisation\":[\"ORG_002\",\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112514571651391488174/content-for-manzarul_1527658061566_do_112514571651391488174_1.0_spine.ecar\",\"size\":167783.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112514571651391488174/artifact/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.thumb.jpeg\",\"children\":[],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/pdf\\\":1,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_112514571651391488174\",\"lastUpdatedBy\":\"0a4e9612-92f8-42b2-a995-a8915d4f6d51\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\",\"Relevant Keywords\",\"Appropriate tags such as Resource Type, Concepts\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\",\"Correct Board, Grade, Subject, Medium\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Appropriate Title, Description\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112514571651391488174/artifact/do_112514571651391488174toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_112513928043757568120\",\"do_112514573623238656175\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"0a4e9612-92f8-42b2-a995-a8915d4f6d51\",\"prevState\":\"Review\",\"size\":167783.0,\"lastPublishedOn\":\"2018-05-30T05:27:41.554+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Content for Manzarul\",\"status\":\"Live\",\"code\":\"org.sunbird.5XO0i1\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/24bc5814688ca8fee4ef6315d969d0bf_1477476723213.jpeg\",\"createdOn\":\"2018-05-30T05:21:12.288+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-30T05:27:40.792+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-06-01T00:00:04.489+0000\",\"createdFor\":[\"0124226794392862720\",\"ORG_001\"],\"creator\":\"content creator_org_002\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527658060792\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_112514571651391488174/content-for-manzarul_1527658061566_do_112514571651391488174_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-30T05:25:53.886+0000\",\"createdBy\":\"6721e33a-9cbb-4c5b-8069-b6236ac98bcd\",\"compatibilityLevel\":4,\"leafNodesCount\":1,\"c_Sunbird_Dev_private_batch_count\":0,\"IL_UNIQUE_ID\":\"do_112514571651391488174\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":22636},{\"subject\":\"Mathematics\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125103855214592001252/course-test_1527146891495_do_1125103855214592001252_1.0_spine.ecar\",\"organisation\":[\"ABC Corporation\",\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125103855214592001252/course-test_1527146891495_do_1125103855214592001252_1.0_spine.ecar\",\"size\":42890.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125103855214592001252/artifact/short_stories_lionandmouse3_1467102846349.thumb.jpg\",\"children\":[],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_1125103855214592001252\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125103855214592001252/artifact/do_1125103855214592001252toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125103858709708801253\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":42890.0,\"lastPublishedOn\":\"2018-05-24T07:28:11.491+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Course Test\",\"status\":\"Live\",\"code\":\"org.sunbird.xiCXgj\",\"description\":\"Course Test desc\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/short_stories_lionandmouse3_1467102846349.jpg\",\"createdOn\":\"2018-05-24T07:24:30.100+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-24T07:28:10.948+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-24T07:28:11.954+0000\",\"createdFor\":[\"01231148953349324812\",\"ORG_001\"],\"creator\":\"Harish Kumar Gangula\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527146890948\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125103855214592001252/course-test_1527146891495_do_1125103855214592001252_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-24T07:25:44.435+0000\",\"createdBy\":\"d57caf73-cd36-4851-9f27-4f75a9c86520\",\"compatibilityLevel\":4,\"leafNodesCount\":0,\"IL_UNIQUE_ID\":\"do_1125103855214592001252\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":29177},{\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112499046776668160146/course-test-two_1526324077890_do_112499046776668160146_2.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112499046776668160146/course-test-two_1526324077890_do_112499046776668160146_2.0_spine.ecar\",\"size\":106854.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112499046776668160146/artifact/imageedit_24_7761433787_1525417836299.thumb.png\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_112499047870480384147\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"identifier\":\"do_112499046776668160146\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Spelling mistakes in the text\",\"Language is simple to understand\",\"Audio (if any) is clear and easy to understand\",\"Can see the content clearly on Desktop and App\",\"Content plays correctly\",\"Correct Board, Grade, Subject, Medium\",\"Appropriate Title, Description\",\"Appropriate tags such as Resource Type, Concepts\",\"Relevant Keywords\",\"Is suitable for children\",\"No Discrimination or Defamation\",\"No Sexual content, Nudity or Vulgarity\",\"No Hate speech, Abuse, Violence, Profanity\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112499046776668160146/artifact/do_112499046776668160146toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1}\",\"childNodes\":[\"do_112499047870480384147\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Live\",\"lastPublishedOn\":\"2018-05-14T18:54:37.881+0000\",\"size\":106854.0,\"name\":\"course test two\",\"status\":\"Live\",\"code\":\"org.sunbird.1lpWED\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124962229128478721138/artifact/imageedit_24_7761433787_1525417836299.png\",\"idealScreenSize\":\"normal\",\"publishComment\":\"\",\"createdOn\":\"2018-05-08T06:55:45.979+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-14T18:54:37.542+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-31T00:00:06.016+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":2.0,\"versionKey\":\"1526324077542\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112499046776668160146/course-test-two_1526324077890_do_112499046776668160146_2.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-05-08T06:58:23.711+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":0,\"compatibilityLevel\":4,\"c_Sunbird_Dev_private_batch_count\":0,\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":75138},{\"subject\":\"Mathematics\",\"channel\":\"505c7c48ac6dc1edc9b08f21db5a571d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131742193909761303/ext-link-usage_1527487689699_do_1125131742193909761303_1.0_spine.ecar\",\"organisation\":[\"QA ORG\",\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131742193909761303/ext-link-usage_1527487689699_do_1125131742193909761303_1.0_spine.ecar\",\"size\":364148.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131742193909761303/artifact/c7a7d301f288f1afe24117ad59083b2a_1475430290462.thumb.jpeg\",\"children\":[],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":3,\\\"application/vnd.ekstep.content-collection\\\":5,\\\"application/vnd.ekstep.ecml-archive\\\":4,\\\"application/vnd.android.package-archive\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_1125131742193909761303\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"Appropriate Title, Description\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\",\"Relevant Keywords\",\"Appropriate tags such as Resource Type, Concepts\",\"Correct Board, Grade, Subject, Medium\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131742193909761303/artifact/do_1125131742193909761303toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":3,\\\"Collection\\\":4,\\\"Story\\\":5}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125131748116070401304\",\"LP_NFTT_18015977\",\"LP_NFT_Collection_1552869\",\"LP_NFTT_15126709\",\"do_1125110622654464001294\",\"do_1125012406331719681200\",\"do_1125012388684431361198\",\"LP_NFTT_1378242\",\"do_1125105374308843521276\",\"LP_NFT_Collection_6264116\",\"LP_NFT_Collection_2821599\",\"LP_NFTT_1735640\",\"LP_NFTT_1982433\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":364148.0,\"lastPublishedOn\":\"2018-05-28T06:08:09.664+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Ext Link usage\",\"status\":\"Live\",\"code\":\"org.sunbird.Mp3A8c\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/c7a7d301f288f1afe24117ad59083b2a_1475430290462.jpeg\",\"createdOn\":\"2018-05-28T05:58:07.327+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-28T06:08:08.781+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-28T08:00:02.657+0000\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"creator\":\"Reviewer User\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527487688781\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125131742193909761303/ext-link-usage_1527487689699_do_1125131742193909761303_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-28T06:06:44.009+0000\",\"createdBy\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"compatibilityLevel\":4,\"leafNodesCount\":8,\"c_Sunbird_Dev_private_batch_count\":1,\"IL_UNIQUE_ID\":\"do_1125131742193909761303\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":31400},{\"subject\":\"Mathematics\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131775385026561305/ext-testing-course_1527487650209_do_1125131775385026561305_1.0_spine.ecar\",\"organisation\":[\"QA ORG\",\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1125131775385026561305/ext-testing-course_1527487650209_do_1125131775385026561305_1.0_spine.ecar\",\"size\":289513.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"KG\"],\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131775385026561305/artifact/b5799249a84d7a574e302281b935a32e_1475479340591.thumb.jpeg\",\"children\":[],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":2,\\\"application/vnd.ekstep.content-collection\\\":3,\\\"application/vnd.ekstep.ecml-archive\\\":2}\",\"contentType\":\"Course\",\"identifier\":\"do_1125131775385026561305\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"audience\":[\"Learner\"],\"publishChecklist\":[\"No Hate speech, Abuse, Violence, Profanity\",\"No Sexual content, Nudity or Vulgarity\",\"No Discrimination or Defamation\",\"Is suitable for children\",\"Content plays correctly\",\"Can see the content clearly on Desktop and App\",\"Audio (if any) is clear and easy to understand\",\"No Spelling mistakes in the text\",\"Language is simple to understand\",\"Relevant Keywords\",\"Appropriate tags such as Resource Type, Concepts\",\"Correct Board, Grade, Subject, Medium\",\"Appropriate Title, Description\"],\"visibility\":\"Default\",\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1125131775385026561305/artifact/do_1125131775385026561305toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":2,\\\"Collection\\\":2,\\\"Story\\\":2}\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"childNodes\":[\"do_1125131779825172481306\",\"do_1125012388684431361198\",\"LP_NFTT_1735640\",\"LP_NFT_Collection_1552869\",\"do_1125110622654464001294\",\"LP_NFTT_1982433\",\"do_1125012406331719681200\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"prevState\":\"Review\",\"size\":289513.0,\"lastPublishedOn\":\"2018-05-28T06:07:30.182+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Ext Testing course\",\"status\":\"Live\",\"code\":\"org.sunbird.j9BAQI\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/b5799249a84d7a574e302281b935a32e_1475479340591.jpeg\",\"createdOn\":\"2018-05-28T06:04:52.492+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-28T06:07:29.483+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-28T06:07:30.630+0000\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"creator\":\"Reviewer User\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1527487649483\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"s3Key\":\"ecar_files/do_1125131775385026561305/ext-testing-course_1527487650209_do_1125131775385026561305_1.0_spine.ecar\",\"lastSubmittedOn\":\"2018-05-28T06:06:17.623+0000\",\"createdBy\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"compatibilityLevel\":4,\"leafNodesCount\":4,\"IL_UNIQUE_ID\":\"do_1125131775385026561305\",\"board\":\"NCERT\",\"resourceType\":\"Course\",\"node_id\":31429},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124834956377456641118/raj-100_1523864386168_do_1124834956377456641118_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124834956377456641118/raj-100_1523864386168_do_1124834956377456641118_1.0_spine.ecar\",\"size\":155474.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124834956377456641118/artifact/1455034327853apple.thumb.jpg\",\"gradeLevel\":[\"KG\",\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\",\"Class 5\",\"Class 6\",\"Class 7\",\"Class 8\",\"Class 9\",\"Class 10\",\"Class 11\",\"Class 12\"],\"children\":[\"do_1124834963596574721119\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":4,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124834956377456641118\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124834956377456641118/artifact/do_1124834956377456641118toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":4}\",\"childNodes\":[\"do_1124834909596467201115\",\"do_1124834903887708161114\",\"do_1124834597969510401107\",\"do_112481447681548288145\",\"do_1124834963596574721119\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":155474.0,\"lastPublishedOn\":\"2018-04-16T07:39:46.152+0000\",\"name\":\"Raj 100\",\"status\":\"Live\",\"code\":\"org.sunbird.hmh4YW\",\"description\":\"Raj 100\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/1455034327853apple.jpg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-16T07:36:53.592+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-16T07:39:45.468+0000\",\"c_Sunbird_Dev_open_batch_count\":1,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:03.020+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523864385468\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124834956377456641118/raj-100_1523864386168_do_1124834956377456641118_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-16T07:39:05.183+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":4,\"compatibilityLevel\":4,\"board\":\"CBSE\",\"resourceType\":[\"Course\"],\"node_id\":71300},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124835868462612481134/raj-test-123_1523875464608_do_1124835868462612481134_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124835868462612481134/raj-test-123_1523875464608_do_1124835868462612481134_1.0_spine.ecar\",\"size\":253198.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124835868462612481134/artifact/planets_1454048789280.thumb.jpg\",\"gradeLevel\":[\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\"],\"children\":[\"do_1124835871952322561135\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":3,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124835868462612481134\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124835868462612481134/artifact/do_1124835868462612481134toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":3}\",\"childNodes\":[\"do_1124834909596467201115\",\"do_1124835851339120641132\",\"do_1124835871952322561135\",\"do_1124835846588579841131\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":253198.0,\"lastPublishedOn\":\"2018-04-16T10:44:24.596+0000\",\"name\":\"Raj Test 123\",\"status\":\"Live\",\"code\":\"org.sunbird.38VtR4\",\"description\":\"Raj Test 123\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/planets_1454048789280.jpg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-16T10:42:27.444+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-16T10:44:23.899+0000\",\"c_Sunbird_Dev_open_batch_count\":1,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:00.746+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523875463899\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124835868462612481134/raj-test-123_1523875464608_do_1124835868462612481134_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-16T10:43:42.876+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":3,\"compatibilityLevel\":4,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":71376},{\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124785353783377921154/rajeev_1523259051957_do_1124785353783377921154_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124785353783377921154/rajeev_1523259051957_do_1124785353783377921154_1.0_spine.ecar\",\"size\":592609.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124785353783377921154/artifact/20c70af6d98b7cfa7b29ab1d31d1cedd_1470223964342.thumb.png\",\"gradeLevel\":[\"KG\",\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\",\"Class 5\",\"Class 6\",\"Class 7\",\"Class 8\",\"Class 9\",\"Class 10\",\"Class 11\",\"Class 12\"],\"children\":[\"do_1124785361392762881155\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.h5p-archive\\\":1,\\\"application/vnd.ekstep.content-collection\\\":1,\\\"application/vnd.ekstep.ecml-archive\\\":1,\\\"video/x-youtube\\\":2}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124785353783377921154\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124785353783377921154/artifact/do_1124785353783377921154toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Worksheet\\\":1,\\\"Story\\\":3}\",\"childNodes\":[\"do_112473631695626240110\",\"do_1124785361392762881155\",\"do_112474267785674752118\",\"do_11246946881515520012\",\"do_11246946840689868811\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":592609.0,\"lastPublishedOn\":\"2018-04-09T07:30:51.936+0000\",\"name\":\"Rajeev\",\"status\":\"Live\",\"code\":\"org.sunbird.MYjoUw\",\"description\":\"Rajeev\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/20c70af6d98b7cfa7b29ab1d31d1cedd_1470223964342.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-09T07:25:13.176+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-09T07:30:51.142+0000\",\"c_Sunbird_Dev_open_batch_count\":1,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:02.844+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523259051142\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124785353783377921154/rajeev_1523259051957_do_1124785353783377921154_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-09T07:27:42.188+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":4,\"compatibilityLevel\":4,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":35311},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112476372676763648176/rajeev-external-content_1522998405061_do_112476372676763648176_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112476372676763648176/rajeev-external-content_1522998405061_do_112476372676763648176_1.0_spine.ecar\",\"size\":299489.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112476372676763648176/artifact/mjrifdya_400x400_1464862503186.thumb.jpg\",\"gradeLevel\":[\"KG\",\"Class 1\",\"Class 2\",\"Class 3\"],\"children\":[\"do_112476392472649728186\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/x-youtube\\\":3}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_112476372676763648176\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112476372676763648176/artifact/do_112476372676763648176toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Worksheet\\\":1,\\\"Resource\\\":1,\\\"Story\\\":1}\",\"childNodes\":[\"do_112448734890074112110\",\"do_112476392472649728186\",\"do_11246946881515520012\",\"do_11246946840689868811\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":299489.0,\"lastPublishedOn\":\"2018-04-06T07:06:45.045+0000\",\"name\":\"Rajeev External Content\",\"status\":\"Live\",\"code\":\"org.sunbird.L6lXdc\",\"description\":\"External Content\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/mjrifdya_400x400_1464862503186.jpg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-06T06:05:11.518+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-06T07:06:44.183+0000\",\"c_Sunbird_Dev_open_batch_count\":0,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:09.406+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1522998404183\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112476372676763648176/rajeev-external-content_1522998405061_do_112476372676763648176_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-06T06:46:07.474+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":3,\"compatibilityLevel\":4,\"board\":\"CBSE\",\"resourceType\":[\"Course\"],\"node_id\":35243},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124834926253588481116/rajeev-test-11111_1523864052940_do_1124834926253588481116_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124834926253588481116/rajeev-test-11111_1523864052940_do_1124834926253588481116_1.0_spine.ecar\",\"size\":139223.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124834926253588481116/artifact/0694babd49c76d206a42a541ead67704_1475228417027.thumb.jpeg\",\"gradeLevel\":[\"KG\",\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\"],\"children\":[\"do_1124834935832903681117\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":4,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124834926253588481116\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124834926253588481116/artifact/do_1124834926253588481116toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":4}\",\"childNodes\":[\"do_1124834909596467201115\",\"do_1124834903887708161114\",\"do_1124834597969510401107\",\"do_112481447681548288145\",\"do_1124834935832903681117\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":139223.0,\"lastPublishedOn\":\"2018-04-16T07:34:12.921+0000\",\"name\":\"Rajeev Test 11111\",\"status\":\"Live\",\"code\":\"org.sunbird.0mP4D3\",\"description\":\"Rajeev Test 11111\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/0694babd49c76d206a42a541ead67704_1475228417027.jpeg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-16T07:30:45.869+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-16T07:34:12.296+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-16T07:34:13.329+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523864052296\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124834926253588481116/rajeev-test-11111_1523864052940_do_1124834926253588481116_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-16T07:33:22.565+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":4,\"compatibilityLevel\":4,\"board\":\"CBSE\",\"resourceType\":[\"Course\"],\"node_id\":71254},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124785262366965761151/rajeev-testing-0904_1523257819074_do_1124785262366965761151_1.0_spine.ecar\",\"channel\":\"in.ekstep\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124785262366965761151/rajeev-testing-0904_1523257819074_do_1124785262366965761151_1.0_spine.ecar\",\"size\":684988.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124785262366965761151/artifact/bd648b9e95d7492f465b8b6558de5134_1470214112411.thumb.png\",\"gradeLevel\":[\"KG\",\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\",\"Class 6\",\"Class 7\",\"Class 8\"],\"children\":[\"do_1124785269863301121152\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.h5p-archive\\\":1,\\\"application/vnd.ekstep.content-collection\\\":5,\\\"application/vnd.ekstep.ecml-archive\\\":1,\\\"video/x-youtube\\\":2}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124785262366965761151\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124785262366965761151/artifact/do_1124785262366965761151toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Worksheet\\\":1,\\\"Collection\\\":4,\\\"Story\\\":3}\",\"childNodes\":[\"do_112291848915984384141\",\"do_112291964537856000114\",\"do_112473631695626240110\",\"do_112474267785674752118\",\"do_112255781089378304134\",\"do_11246946881515520012\",\"do_1124785269863301121152\",\"do_11246946840689868811\",\"do_11225568180199424014\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"c_mybatch_course_count\":3,\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":684988.0,\"lastPublishedOn\":\"2018-04-09T07:10:19.013+0000\",\"name\":\"Rajeev Testing 09/04\",\"status\":\"Live\",\"code\":\"org.sunbird.njt4zt\",\"description\":\"Rajeev Testing 09/04\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/bd648b9e95d7492f465b8b6558de5134_1470214112411.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-09T07:06:37.252+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-09T07:10:18.037+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:08.165+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523257818037\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124785262366965761151/rajeev-testing-0904_1523257819074_do_1124785262366965761151_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-09T07:08:53.650+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":4,\"compatibilityLevel\":4,\"c_Sunbird_Dev_private_batch_count\":0,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":35313},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112489939558170624165/sample-course_1524718451271_do_112489939558170624165_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112489939558170624165/sample-course_1524718451271_do_112489939558170624165_1.0_spine.ecar\",\"size\":46061.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112489939558170624165/artifact/book2_245_1466489432_1466489432820.thumb.png\",\"gradeLevel\":[\"Class 1\"],\"children\":[\"do_112489940114448384166\",\"do_112490491253825536192\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/epub\\\":1,\\\"application/vnd.ekstep.content-collection\\\":2,\\\"application/vnd.ekstep.ecml-archive\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_112489939558170624165\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112489939558170624165/artifact/do_112489939558170624165toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":2,\\\"Resource\\\":1,\\\"Story\\\":1}\",\"childNodes\":[\"do_112490491253825536192\",\"LP_NFT_Content2_Content1_6114012\",\"do_112489916589834240157\",\"do_112489940114448384166\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":46061.0,\"lastPublishedOn\":\"2018-04-26T04:54:11.252+0000\",\"concepts\":[\"BIO3\",\"LO4\"],\"name\":\"Sample Course\",\"status\":\"Live\",\"code\":\"org.sunbird.CP1xlE\",\"description\":\"Sample Course\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/book2_245_1466489432_1466489432820.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-25T10:07:04.971+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-26T04:54:09.977+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-26T04:54:11.741+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1524718449977\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112489939558170624165/sample-course_1524718451271_do_112489939558170624165_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-26T04:50:31.872+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":2,\"compatibilityLevel\":4,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":72318},{\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112499037821370368144/test-coures-one_1526324077009_do_112499037821370368144_2.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112499037821370368144/test-coures-one_1526324077009_do_112499037821370368144_2.0_spine.ecar\",\"size\":124066.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112499037821370368144/artifact/imageedit_7_9053957983_1525417638250.thumb.png\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_112499038427430912145\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"e48077e1-a3bf-462a-bd19-2bb6a733abd7\",\"identifier\":\"do_112499037821370368144\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112499037821370368144/artifact/do_112499037821370368144toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1}\",\"childNodes\":[\"do_112499038427430912145\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"e48077e1-a3bf-462a-bd19-2bb6a733abd7\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Live\",\"size\":124066.0,\"lastPublishedOn\":\"2018-05-14T18:54:36.980+0000\",\"name\":\"test coures one\",\"status\":\"Live\",\"code\":\"org.sunbird.vtsBr8\",\"description\":\"Untitled Collection\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124962212900208641137/artifact/imageedit_7_9053957983_1525417638250.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-05-08T06:37:32.803+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-05-14T18:54:36.546+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-05-14T18:54:37.481+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":2.0,\"versionKey\":\"1526324076546\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112499037821370368144/test-coures-one_1526324077009_do_112499037821370368144_2.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-05-08T06:39:38.338+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":0,\"compatibilityLevel\":4,\"board\":\"State (Andhra Pradesh)\",\"resourceType\":\"Course\",\"node_id\":75133},{\"keywords\":[\"Tag123\",\"Story\",\"Story\"],\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11249218836022067213/test-course1_1524925478088_do_11249218836022067213_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11249218836022067213/test-course1_1524925478088_do_11249218836022067213_1.0_spine.ecar\",\"size\":379173.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_11249218836022067213/artifact/courseimage_1524054774294.thumb.jpg\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_11249218887626752014\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":2,\\\"application/pdf\\\":1,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_11249218836022067213\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_11249218836022067213/artifact/do_11249218836022067213toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":3}\",\"childNodes\":[\"do_1124919618509701121112\",\"do_11249218887626752014\",\"do_1124919550414028801104\",\"do_11249122894346649611\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"lastPublishedOn\":\"2018-04-28T14:24:38.076+0000\",\"size\":379173.0,\"concepts\":[\"SC8\"],\"name\":\"Test Course1\",\"status\":\"Live\",\"code\":\"org.sunbird.gCsM9Y\",\"description\":\"Test Course\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112485056708968448113/artifact/courseimage_1524054774294.jpg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-28T14:22:16.939+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-28T14:24:37.279+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-28T14:24:38.474+0000\",\"creator\":\"Cretation User\",\"createdFor\":[\"0123653943740170242\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1524925477279\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_11249218836022067213/test-course1_1524925478088_do_11249218836022067213_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-28T14:23:53.476+0000\",\"createdBy\":\"874ed8a5-782e-4f6c-8f36-e0288455901e\",\"leafNodesCount\":3,\"compatibilityLevel\":4,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":72497},{\"keywords\":[\"mai\",\"Story\",\"Story\"],\"subject\":\"Mathematics\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124708031109939201177/test-course-1_1522315155833_do_1124708031109939201177_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_1124708031109939201177/test-course-1_1522315155833_do_1124708031109939201177_1.0_spine.ecar\",\"size\":311648.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124708031109939201177/artifact/ead47a749021c19174ace5cb83c14602_1476434350202.thumb.jpeg\",\"gradeLevel\":[\"Class 5\"],\"children\":[\"do_1124708044270141441178\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":2,\\\"application/vnd.ekstep.ecml-archive\\\":1,\\\"video/x-youtube\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_1124708031109939201177\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_1124708031109939201177/artifact/do_1124708031109939201177toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Worksheet\\\":1,\\\"Resource\\\":1,\\\"Collection\\\":1}\",\"childNodes\":[\"do_1124708044270141441178\",\"do_1124707615343738881153\",\"do_112469399503585280117\",\"do_11246946840689868811\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":311648.0,\"lastPublishedOn\":\"2018-03-29T09:19:15.813+0000\",\"concepts\":[\"LO4\",\"C6\",\"BIO3\"],\"name\":\"Test-Course-1\",\"status\":\"Live\",\"code\":\"org.sunbird.eyZihk\",\"description\":\"Test-course-1\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/ead47a749021c19174ace5cb83c14602_1476434350202.jpeg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-03-29T09:13:52.885+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-03-29T09:19:14.720+0000\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-03-29T09:19:16.295+0000\",\"creator\":\"Sunil Pandith\",\"createdFor\":[\"0123673542904299520\",\"0123673689120112640\",\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1522315154720\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_1124708031109939201177/test-course-1_1522315155833_do_1124708031109939201177_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-03-29T09:17:35.812+0000\",\"createdBy\":\"159e93d1-da0c-4231-be94-e75b0c226d7c\",\"leafNodesCount\":2,\"compatibilityLevel\":4,\"board\":\"State (Andhra Pradesh)\",\"resourceType\":[\"Course\"],\"node_id\":34370},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11247440012845875211/testbook-postfix_1522754475951_do_11247440012845875211_2.0_spine.ecar\",\"channel\":\"01247650764698419210\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11247440012845875211/testbook-postfix_1522754475951_do_11247440012845875211_2.0_spine.ecar\",\"size\":153108.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_11247440012845875211/artifact/6f00e56680fa722f16bd8a282480c786_1476254079786.thumb.jpeg\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_11247440240096870412\",\"do_11247440398840627213\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":2,\\\"application/vnd.ekstep.ecml-archive\\\":2}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_11247440012845875211\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_11247440012845875211/artifact/do_11247440012845875211toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":2,\\\"Story\\\":2}\",\"childNodes\":[\"do_11247440398840627213\",\"do_112474267785674752118\",\"do_11247424678500761617\",\"do_11247440240096870412\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":153108.0,\"lastPublishedOn\":\"2018-04-03T11:21:15.943+0000\",\"name\":\"Testbook-Postfix\",\"status\":\"Live\",\"code\":\"org.sunbird.zkEzwn\",\"description\":\"Testbook-Postfix\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/6f00e56680fa722f16bd8a282480c786_1476254079786.jpeg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-03T11:19:53.122+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-03T11:21:15.071+0000\",\"c_Sunbird_Dev_open_batch_count\":2,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-26T00:00:05.493+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":2.0,\"versionKey\":\"1522754475901\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_11247440012845875211/testbook-postfix_1522754475951_do_11247440012845875211_2.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-03T11:20:23.712+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":2,\"compatibilityLevel\":4,\"board\":\"NCERT\",\"resourceType\":[\"Course\"],\"node_id\":35110},{\"subject\":\"English\",\"downloadUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112481468925796352148/testing_1523623017545_do_112481468925796352148_1.0_spine.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_112481468925796352148/testing_1523623017545_do_112481468925796352148_1.0_spine.ecar\",\"size\":374350.0}},\"mimeType\":\"application/vnd.ekstep.content-collection\",\"objectType\":\"Content\",\"appIcon\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112481468925796352148/artifact/jhk_1465380259344.thumb.jpg\",\"gradeLevel\":[\"KG\"],\"children\":[\"do_112481473385750528149\"],\"appId\":\"ekstep_portal\",\"contentEncoding\":\"gzip\",\"mimeTypesCount\":\"{\\\"text/x-url\\\":1,\\\"application/vnd.ekstep.content-collection\\\":2,\\\"application/vnd.ekstep.ecml-archive\\\":1}\",\"contentType\":\"Course\",\"lastUpdatedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"identifier\":\"do_112481468925796352148\",\"audience\":[\"Learner\"],\"toc_url\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112481468925796352148/artifact/do_112481468925796352148toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1,\\\"Collection\\\":1,\\\"Story\\\":1}\",\"childNodes\":[\"do_112481447681548288145\",\"do_112481473385750528149\",\"do_1124785952672563201158\",\"do_112474267785674752118\"],\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"3b34c469-460b-4c20-8756-c5fce2de9e69\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"prevState\":\"Review\",\"size\":374350.0,\"lastPublishedOn\":\"2018-04-13T12:36:57.527+0000\",\"concepts\":[\"SC8\"],\"name\":\"Testing\",\"status\":\"Live\",\"code\":\"org.sunbird.C8vK4F\",\"description\":\"Testing\",\"medium\":\"English\",\"posterImage\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/jhk_1465380259344.jpg\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-04-13T10:53:32.230+0000\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2018-04-13T12:36:56.814+0000\",\"c_Sunbird_Dev_open_batch_count\":1,\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2018-04-20T05:10:03.182+0000\",\"creator\":\"N. T. RAO . creator_org_001\",\"createdFor\":[\"ORG_001\"],\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1523623016814\",\"idealScreenDensity\":\"hdpi\",\"s3Key\":\"ecar_files/do_112481468925796352148/testing_1523623017545_do_112481468925796352148_1.0_spine.ecar\",\"framework\":\"NCF\",\"lastSubmittedOn\":\"2018-04-13T11:03:32.104+0000\",\"createdBy\":\"6d4da241-a31b-4041-bbdb-dd3a898b3f85\",\"leafNodesCount\":2,\"compatibilityLevel\":4,\"board\":\"CBSE\",\"resourceType\":[\"Course\"],\"node_id\":69310}],\"count\":21,\"params\":{\"resmsgId\":\"a0201f37-0de4-4c08-9b50-1868cb8a322c\",\"apiId\":\"ekstep.composite-search.search\"}}";
    Map<String, Object> result = mapper.readValue(resStr, Map.class);
    
//    if (null != result && !result.isEmpty()) {
      section.putAll(result);
      section.remove(JsonKey.PARAMS);
      Map<String, Object> tempMap = (Map<String, Object>) result.get(JsonKey.PARAMS);
      section.put(JsonKey.RES_MSG_ID, tempMap.get(JsonKey.RES_MSG_ID));
      section.put(JsonKey.API_ID, tempMap.get(JsonKey.API_ID));
//    } else {
//      ProjectLogger.log(
//          "Search query result from ekstep is null or empty for query " + query, LoggerEnum.INFO);
//    }
  }

  @SuppressWarnings("unchecked")
  /**
   * combine both requested page filters with default page filters.
   *
   * @param filters
   * @param reqFilters
   */
  private void applyFilters(Map<String, Object> filters, Map<String, Object> reqFilters) {
    if (null != reqFilters) {
      Set<Entry<String, Object>> entrySet = reqFilters.entrySet();
      for (Entry<String, Object> entry : entrySet) {
        String key = entry.getKey();
        if (filters.containsKey(key)) {
          Object obj = entry.getValue();
          if (obj instanceof List) {
            if (filters.get(key) instanceof List) {
              Set<Object> set = new HashSet<>((List<Object>) filters.get(key));
              set.addAll((List<Object>) obj);
              ((List<Object>) filters.get(key)).clear();
              ((List<Object>) filters.get(key)).addAll(set);
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, obj);
            } else {
              if (!(((List<Object>) obj).contains(filters.get(key)))) {
                ((List<Object>) obj).add(filters.get(key));
              }
              filters.put(key, obj);
            }
          } else if (obj instanceof Map) {
            filters.put(key, obj);
          } else {
            if (filters.get(key) instanceof List) {
              if (!(((List<Object>) filters.get(key)).contains(obj))) {
                ((List<Object>) filters.get(key)).add(obj);
              }
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, obj);
            } else {
              List<Object> list = new ArrayList<>();
              list.add(filters.get(key));
              list.add(obj);
              filters.put(key, list);
            }
          }
        } else {
          filters.put(key, entry.getValue());
        }
      }
    }
  }

  private Map<String, Object> getPageSetting(Map<String, Object> pageDO) {

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(JsonKey.NAME, pageDO.get(JsonKey.NAME));
    responseMap.put(JsonKey.ID, pageDO.get(JsonKey.ID));

    if (pageDO.containsKey(JsonKey.APP_MAP) && null != pageDO.get(JsonKey.APP_MAP)) {
      responseMap.put(JsonKey.APP_SECTIONS, parsePage(pageDO, JsonKey.APP_MAP));
    }
    if (pageDO.containsKey(JsonKey.PORTAL_MAP) && null != pageDO.get(JsonKey.PORTAL_MAP)) {
      responseMap.put(JsonKey.PORTAL_SECTIONS, parsePage(pageDO, JsonKey.PORTAL_MAP));
    }
    return responseMap;
  }

  private void removeUnwantedData(Map<String, Object> map, String from) {
    map.remove(JsonKey.CREATED_DATE);
    map.remove(JsonKey.CREATED_BY);
    map.remove(JsonKey.UPDATED_DATE);
    map.remove(JsonKey.UPDATED_BY);
    if (from.equalsIgnoreCase("getPageData")) {
      map.remove(JsonKey.STATUS);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parsePage(Map<String, Object> pageDO, String mapType) {
    List<Map<String, Object>> sections = new ArrayList<>();
    String sectionQuery = (String) pageDO.get(mapType);
    ObjectMapper mapper = new ObjectMapper();
    try {
      Object[] arr = mapper.readValue(sectionQuery, Object[].class);
      for (Object obj : arr) {
        Map<String, Object> sectionMap = (Map<String, Object>) obj;
        Response sectionResponse =
            cassandraOperation.getRecordById(
                pageSectionDbInfo.getKeySpace(),
                pageSectionDbInfo.getTableName(),
                (String) sectionMap.get(JsonKey.ID));

        List<Map<String, Object>> sectionResult =
            (List<Map<String, Object>>) sectionResponse.getResult().get(JsonKey.RESPONSE);
        if (null != sectionResult && !sectionResult.isEmpty()) {
          sectionResult.get(0).put(JsonKey.GROUP, sectionMap.get(JsonKey.GROUP));
          sectionResult.get(0).put(JsonKey.INDEX, sectionMap.get(JsonKey.INDEX));
          removeUnwantedData(sectionResult.get(0), "");
          sections.add(sectionResult.get(0));
        }
      }
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return sections;
  }

  private void validateOrg(String orgId) {
    Response result =
        cassandraOperation.getRecordById(orgDbInfo.getKeySpace(), orgDbInfo.getTableName(), orgId);
    List<Map<String, Object>> list = (List<Map<String, Object>>) result.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(list)) {
      throw new ProjectCommonException(
          ResponseCode.invalidOrgId.getErrorCode(),
          ResponseCode.invalidOrgId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
