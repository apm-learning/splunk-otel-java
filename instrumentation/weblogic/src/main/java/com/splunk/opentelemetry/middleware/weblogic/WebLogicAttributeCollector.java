/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.splunk.opentelemetry.middleware.weblogic;

import com.splunk.opentelemetry.javaagent.bootstrap.MiddlewareHolder;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

public class WebLogicAttributeCollector {
  private static final String CONTEXT_ATTRIBUTE_NAME = "otel.weblogic.attributes";
  public static final String REQUEST_ATTRIBUTE_NAME = "otel.middleware";

  public static void attachMiddlewareAttributes(HttpServletRequest servletRequest) {
    WebLogicEntity.Request request = WebLogicEntity.Request.wrap(servletRequest);

    Map<?, ?> attributes = fetchMiddlewareAttributes(request.getContext());
    request.instance.setAttribute(REQUEST_ATTRIBUTE_NAME, attributes);
  }

  private static Map<?, ?> fetchMiddlewareAttributes(WebLogicEntity.Context context) {
    if (context.instance == null) {
      return null;
    }

    Object value = context.instance.getAttribute(CONTEXT_ATTRIBUTE_NAME);

    if (value instanceof Map<?, ?>) {
      return (Map<?, ?>) value;
    }

    // Do this here to avoid duplicate work
    storeMiddlewareIdentity(context);

    Map<String, String> middleware = collectMiddlewareAttributes(context);
    context.instance.setAttribute(CONTEXT_ATTRIBUTE_NAME, middleware);
    return middleware;
  }

  private static void storeMiddlewareIdentity(WebLogicEntity.Context context) {
    MiddlewareHolder.trySetName("WebLogic Server");
    MiddlewareHolder.trySetVersion(detectVersion(context));
  }

  private static Map<String, String> collectMiddlewareAttributes(WebLogicEntity.Context context) {
    WebLogicEntity.Bean applicationBean = context.getBean();
    WebLogicEntity.Bean webServerBean = context.getServer().getBean();
    WebLogicEntity.Bean serverBean = webServerBean.getParent();
    WebLogicEntity.Bean clusterBean = WebLogicEntity.Bean.wrap(serverBean.getAttribute("Cluster"));
    WebLogicEntity.Bean domainBean = serverBean.getParent();

    Map<String, String> attributes = new HashMap<>();
    attributes.put("middleware.weblogic.domain", domainBean.getName());
    attributes.put("middleware.weblogic.cluster", clusterBean.getName());
    attributes.put("middleware.weblogic.server", webServerBean.getName());
    attributes.put("middleware.weblogic.application", applicationBean.getName());

    return attributes;
  }

  private static String detectVersion(WebLogicEntity.Context context) {
    String serverInfo = context.instance.getServerInfo();

    if (serverInfo != null) {
      for (String token : serverInfo.split(" ")) {
        if (token.length() > 0 && Character.isDigit(token.charAt(0))) {
          return token;
        }
      }
    }

    return "";
  }
}
