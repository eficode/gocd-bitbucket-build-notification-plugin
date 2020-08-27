/*
 * Copyright 2020 Eficode Oy
 * Copyright 2018 ThoughtWorks, Inc.
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

package com.eficode.bitbucketnotifier.executors;

import com.eficode.bitbucketnotifier.PluginRequest;
import com.eficode.bitbucketnotifier.PluginSettings;
import com.eficode.bitbucketnotifier.RequestExecutor;
import com.eficode.bitbucketnotifier.requests.StageStatusRequest;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class StageStatusRequestExecutor implements RequestExecutor {
    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private final StageStatusRequest request;
    private final PluginRequest pluginRequest;

    public StageStatusRequestExecutor(StageStatusRequest request, PluginRequest pluginRequest) {
        this.request = request;
        this.pluginRequest = pluginRequest;
    }

    @Override
    public GoPluginApiResponse execute() throws Exception {
        HashMap<String, Object> responseJson = new HashMap<>();
        try {
            sendNotification();
            responseJson.put("status", "success");
        } catch (Exception e) {
            responseJson.put("status", "failure");
            responseJson.put("messages", Arrays.asList(e.getMessage()));
        }
        return new DefaultGoPluginApiResponse(200, GSON.toJson(responseJson));
    }

    protected void sendNotification() throws Exception {
        PluginSettings pluginSettings = pluginRequest.getPluginSettings();
        Map material = this.request.pipeline.buildCause.get(0).material;

        if (material.get("type").equals("git")) {
            LinkedTreeMap gitConfig = (LinkedTreeMap) material.get("git-configuration");
            String gitUrl = (String) gitConfig.get("url");

            if (gitUrl.contains(pluginSettings.getApiUrl())) {
                String revision = this.request.pipeline.buildCause.get(0).modifications.get(0).revision;
                updateBitbucket(pluginSettings, revision);
            }
        }
    }

    private void updateBitbucket(PluginSettings pluginSettings, String revision) throws Exception {
        URL url = new URL(pluginSettings.getApiUrl() + "/rest/build-status/1.0/commits/" + revision);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", getAuthHeader(pluginSettings));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        OutputStream os = connection.getOutputStream();
        String buildUrl = pluginSettings.getGoServerUrl() + "/go/pipelines/value_stream_map/" + request.pipeline.name + "/" + request.pipeline.counter;

        Map<String, String> body = new HashMap<>();
        body.put("state", parseBuildState(request.pipeline.stage.state));
        body.put("key", revision);
        body.put("name", request.pipeline.counter);
        body.put("url", buildUrl);

        byte[] input = new GsonBuilder().create().toJson(body).getBytes("utf-8");
        os.write(input, 0, input.length);

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String responseLine;

        while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
        }

        System.out.println(response.toString());
        connection.getInputStream().close();
    }

    private String parseBuildState(String stageState) {
        switch (stageState) {
            case "Building":
                return "INPROGRESS";
            case "Passed":
                return "SUCCESSFUL";
            case "Failed":
            case "Cancelled":
                return "FAILED";
        }
        throw new IllegalArgumentException("Unknown state for stage: " + stageState);
    }

    private String getAuthHeader(PluginSettings pluginSettings) throws Exception {
        String auth = pluginSettings.getApiUser()+ ":" + pluginSettings.getApiKey();
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes("utf-8"));
        return "Basic " + new String(encodedAuth);
    }
}
