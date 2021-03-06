package org.anax.framework.reporting.service;

import lombok.extern.slf4j.Slf4j;
import org.anax.framework.model.TestMethod;
import org.anax.framework.reporting.model.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ZephyrZAPIService {

    @Autowired
    @Qualifier("zapiRestTemplate")
    protected RestTemplate restTemplate;

    @Autowired
    protected AnaxZapiVersionResolver versionResolver;

    @Value("${zapi.url:https:NOT_CONFIGURED}")  private String zapiUrl;
    @Value("${jira.url:https:NOT_CONFIGURED}")  private String jiraUrl;
    @Value("${jira.search.tc.attribute:label}") private String attribute;
    @Value("${zapi.status.pass.code:1}")        private String pass;

    /**
     * Get cycle id from cycle name at UnSchedule
     * @param projectName
     * @param cycleName
     * @return
     */
    public String getCycleId(String projectName, String versionName , String cycleName){
        String projectId = getProjectId(projectName);
        String versionId = getVersionId(projectId,versionName);
        ResponseEntity<Map> entity = restTemplate.exchange(zapiUrl + "cycle?projectId=" + projectId+"&versionId="+versionId, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        Map.Entry<String, Map<Object, Object>> result = new Cycles(entity.getBody()).getContents().entrySet().stream().filter(x->x.getValue().get("name").equals(cycleName)).findFirst().orElse(null);
        if(result == null)
            log.error("No Cycle found on project: {} with this name: {}",projectName,cycleName);
        return (result != null) ? result.getKey() : null;
    }


    /**
     * Get project id from project name
     * @param projectName
     * @return
     */
    public String getProjectId(String projectName) {
        ProjectList projectList = restTemplate.getForObject(zapiUrl + "util/project-list", ProjectList.class);
        LabelValue labelValue = projectList.getOptions().stream().filter(data -> data.getLabel().equals(projectName)).findFirst().orElse(null);
        if(labelValue == null)
            log.error("Check: No Project found with this name: {} , program will exit!!!",projectName);
        return (labelValue != null) ? labelValue.getValue() : "";
    }

    /**
     * Get cycle id from cycle name at unschedule
     * @param projectName
     * @param cycleName
     * @return
     */
    public String getCycleIdUnderUnSchedule(String projectName, String cycleName){
        String projectId = getProjectId(projectName);
        ResponseEntity<Map> entity = restTemplate.exchange(zapiUrl + "cycle?projectId=" + projectId+"&versionId=-1", HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        Map.Entry<String, Map<Object, Object>> result = new Cycles(entity.getBody()).getContents().entrySet().stream().filter(x->x.getValue().get("name").equals(cycleName)).findFirst().orElse(null);
        return (result != null) ? result.getKey() : null;
    }


    /**
     * Get version id from version name
     * @param projectId
     * @param versionName
     * @return
     */
    public String getVersionId(String projectId, String versionName){
        ResponseEntity<List<Version>> versions = restTemplate.exchange(jiraUrl +"project/"+projectId+"/versions", HttpMethod.GET, new HttpEntity<>(getHeaders()),  new ParameterizedTypeReference<List<Version>>() {});
        return versionResolver.getVersionFromJIRA(versionName, versions);
    }


    /**
     * Returns the test id from label or either the name - empty string in case no tc was found
     * @param projectName
     * @param versionName
     * @param cycleName
     * @param attributeValue
     * @throws Exception
     */
    public String getIssueExecutionIdViaAttributeValue(String projectName, String versionName, String cycleName, String attributeValue) {
        String projectId = getProjectId(projectName);
        String versionId = getVersionId(projectId, versionName);
        String cycleId = getCycleId(projectName, versionName, cycleName);

        try {
            try { Thread.sleep(1000); } catch (Exception e) {}
            return filterDataByAttributeValue((JSONArray) new JSONObject(restTemplate.exchange(zapiUrl + "execution?projectId=" + projectId + "&versionId=" + versionId + "&cycleId=" + cycleId, HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class).getBody()).get("executions"),attribute, attributeValue).get("id").toString();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Check !! Issue with this label: {} was not found on project: '{}' at version: '{}' and cycle: '{}'",attributeValue,projectName,versionName,cycleName);
            return "";
        }
    }


    /**
     * Returns test step execution Id
     * @param tcExecutionId
     * @param ordering
     * @return
     */
    public String getTestStepExecutionId(String tcExecutionId, int ordering) {
        restTemplate.exchange(zapiUrl + "execution/" + tcExecutionId + "?expand=checksteps", HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class);
        try {
            return getTestStepIdViaOrder(restTemplate.exchange(zapiUrl + "stepResult?executionId=" + tcExecutionId, HttpMethod.GET, new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<List<TestStepExecution>>() {}).getBody(),ordering);
        } catch (Exception e) {
            e.printStackTrace();
            log.info("Test Step execution id do not found");
            return "";
        }
    }

    /**
     * Get test steps executions
     * @param tcExecutionId
     * @return
     */
    public List getTestSteps(String tcExecutionId){
        restTemplate.exchange(zapiUrl + "execution/" + tcExecutionId + "?expand=checksteps", HttpMethod.GET, new HttpEntity<>(getHeaders()), String.class);
        return restTemplate.exchange(zapiUrl + "stepResult?executionId=" + tcExecutionId, HttpMethod.GET, new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<List<TestStepExecution>>() {}).getBody();
    }

    /**
     * Update test step status
     * @param testStepExecutionId
     * @param status
     */
    public void updateTestStepStatus(String testStepExecutionId, String status, TestMethod testMethod){
        Map postBody = new HashMap();
        postBody.put("status", status);
        if (!status.equals(pass) && !StringUtils.isEmpty(testMethod.getDescription()))//not pass and has description
            postBody.put("comment", testMethod.getDescription());

        restTemplate.exchange(zapiUrl + "stepResult/" + testStepExecutionId, HttpMethod.PUT, new HttpEntity<>(postBody, getHeaders()), String.class);
    }

    /**
     * Add attachement on execution
     * @param executionId
     * @param file
     */
    public void addTcExecutionAttachments(String executionId, File file) {
        LinkedMultiValueMap postBody = new LinkedMultiValueMap();
        postBody.add("file", new FileSystemResource(file));

        restTemplate.exchange(zapiUrl + "attachment?entityId=" + executionId + "&entityType=EXECUTION", HttpMethod.POST, new HttpEntity<>(postBody, getMultiPartHeaders()), String.class);
    }

    /**
     * Add attachment on execution step
     * @param executionStepId
     * @param file
     */
    public void addStepExecutionAttachments(String executionStepId, File file) {
        LinkedMultiValueMap postBody = new LinkedMultiValueMap();
        postBody.add("file", new FileSystemResource(file));

        restTemplate.exchange(zapiUrl + "attachment?entityId=" + executionStepId + "&entityType=stepResult", HttpMethod.POST, new HttpEntity<>(postBody, getMultiPartHeaders()), String.class);
    }

    /**
     * Clone a cycle (including executions) from default cycle to specific cycle
     * @param projectName
     * @param versionName
     * @param cycleClone
     * @param originalCycleName
     */
    public void cloneCycleToVersion(String projectName,String versionName,CycleClone cycleClone,String originalCycleName){
        String projectId = getProjectId(projectName);
        String versionId = getVersionId(projectId,versionName);
        String cycleId = getCycleIdUnderUnSchedule(projectName,originalCycleName);

        cycleClone.setProjectId(projectId);
        cycleClone.setVersionId(versionId);
        cycleClone.setClonedCycleId(cycleId);
        restTemplate.exchange(zapiUrl+"cycle",HttpMethod.POST ,new HttpEntity(cycleClone, getHeaders()), CycleClone.class);
    }

    /**
     * Update execution results as bulk
     * @param results
     */
    public void updateBulkResults(Results results){
        restTemplate.exchange(zapiUrl+"execution/updateBulkStatus/",HttpMethod.PUT ,new HttpEntity(results, getHeaders()), Results.class);
    }

    /**
     * Update test step status
     * @param tcExecutionID
     * @param comment
     */
    public void updateTestExecutionComment(String tcExecutionID, String comment){
        Map postBody = new HashMap();
        postBody.put("comment",comment);

        restTemplate.exchange(zapiUrl + "execution/" + tcExecutionID+"/execute", HttpMethod.PUT, new HttpEntity<>(postBody,getHeaders()), String.class);
    }


    /**
     * Update test execution bugs
     * @param tcExecutionID
     * @param bugs
     */
    public void updateTestExecutionBugs(String tcExecutionID,List<String> bugs){
        Map postBody = new HashMap();
        postBody.put("defectList", bugs);
        postBody.put("updateDefectList", "true");

        restTemplate.exchange(zapiUrl + "execution/" + tcExecutionID+"/execute", HttpMethod.PUT, new HttpEntity<>(postBody,getHeaders()), String.class);
    }


    //Find the correct object in a jsonArray based on the value of an attribute,Create a list of label and then get the index of the JsonObject with this label
    private JSONObject filterDataByAttributeValue(JSONArray jsonArray, String attribute, String labelValue) throws JSONException {
        int index;
        ArrayList<String> values = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            values.add(jsonArray.getJSONObject(i).getString(attribute).toLowerCase());
        }
        index = IntStream.range(0, values.size()).filter(i -> values.get(i).contains(labelValue.toLowerCase())).findFirst().getAsInt();
        return jsonArray.getJSONObject(index);
    }

    //Search all the steps of tc executionId , returns the testStepId according the orderId
    private String getTestStepIdViaOrder(List<TestStepExecution> testStepExecutions, int ordering){

        TestStepExecution result = testStepExecutions.stream().filter(it->it.getOrderId().equals(ordering+1)).findFirst().orElse(null);
        return (result != null) ? String.valueOf(result.getId()) : "";
    }

    private HttpHeaders getHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.APPLICATION_JSON );
        return headers;
    }

    private HttpHeaders getMultiPartHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Atlassian-Token", "nocheck");
        headers.setContentType( MediaType.MULTIPART_FORM_DATA );
        return headers;
    }
}
