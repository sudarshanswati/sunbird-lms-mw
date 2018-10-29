package org.sunbird.user.service;

import java.util.Map;

import org.sunbird.common.request.Request;
import org.sunbird.models.user.User;

public interface UserService {

  Map<String, Object> getUserByUserIdAndOrgId(String userId, String orgId);

  User getUserById(String userId);

  void validateUserId(Request request);

  Map<String, Object> esGetUserById(String userId);

  void syncProfileVisibility(
      String userId, Map<String, Object> userDataMap, Map<String, Object> userPrivateDataMap);
}
