package org.sunbird.user.actors;

import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.learner.util.Util;

@ActorConfig(
  tasks = {"userCurrentLogin"},
  asyncTasks = {}
)
public class UserLoginActor extends UserBaseActor {

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, JsonKey.USER);
    ExecutionContext.setRequestId(request.getRequestId());
    String operation = request.getOperation();

    if (operation.equalsIgnoreCase("userCurrentLogin")) {
      updateUserLoginTime(request);
    } else {
      onReceiveUnsupportedOperation("UserLoginActor");
    }
  }

	/**
	 * Updates user's current login time in Keycloak.
	 *
	 * @param actorMessage Request containing user ID.
	 */
	private  void updateUserLoginTime(Request actorMessage) {
		String userId = (String) actorMessage.getRequest().get(JsonKey.USER_ID);
		Response response = new Response();
		response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
		sender().tell(response, self());
		if (Boolean.parseBoolean(PropertiesCache.getInstance().getProperty(JsonKey.IS_SSO_ENABLED))) {

			String userLoginTime = Long.toString(System.currentTimeMillis());

			// Update the login time in Key Cloak
			boolean loginTimeResponse = getSSOManager().addUserLoginTime(userId, userLoginTime);

			ProjectLogger.log("UserLoginActor:updateUserLoginTime: keycloak response = " + loginTimeResponse);

			// Update the login time in Elastic Search
			boolean loginTimeResponseFromES = getUserService().addUserLoginTime(userId, userLoginTime);

			ProjectLogger.log("UserLoginActor:updateUserLoginTime: ES response = " + loginTimeResponseFromES);
		}
	}

}
