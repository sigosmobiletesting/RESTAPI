package com.mc.restfulAPI;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mc.common.Log;
import com.mc.common.rest.RestUtils;
import com.mc.common.rest.Status;
import com.mc.common.util.CommonUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.xerces.util.URI;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.net.URL;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by mhe on 3/8/2017.
 */
public class TestMonitorData
{
    String userName;
    String password;

    String accessServerURL;

    int mcd = 0;

    String monitorName = "";
    String startTime = "";
    String endTime = "";

    static public void main(String args[])
    {
        TestMonitorData test = new TestMonitorData();

        try {
            test.runTest();
        } catch (Exception e) {
            Log.error(e.getMessage());
        }
    }

    public TestMonitorData() {
        userName = System.getProperty("user_name");
        password = System.getProperty("password");

        if (StringUtils.isBlank(userName) || StringUtils.isBlank(password))
            throw new RuntimeException("Invalid credentials - cannot proceed!");

        accessServerURL = System.getProperty("access_server_url");

        if (StringUtils.isBlank(accessServerURL))
            throw new RuntimeException("Access Serverl url invalid - cannot proceed!");

        try {
            new URL(accessServerURL);
        } catch (Exception e) {
            Log.error(e);

            throw new RuntimeException("Access Serverl url invalid - cannot proceed!");
        }

        boolean invalidMcd = false;

        String device = System.getProperty("mcd");

        try {
            mcd = Integer.parseInt(device);

            if (mcd <= 0)
                invalidMcd = true;
        } catch(Exception e) {
            Log.error(e);

            invalidMcd = true;
        }

        if (invalidMcd)
            throw new RuntimeException("No device provided " + device);

        startTime = System.getProperty("start_time");
        endTime = System.getProperty("end_time");

        if (StringUtils.isBlank(startTime) && StringUtils.isBlank(endTime)) {
            Calendar calendar = Calendar.getInstance();

            Date nowDate = calendar.getTime();

            calendar.add(Calendar.DAY_OF_YEAR, -3);

            Date weekDate = calendar.getTime();

            try {
                startTime = CommonUtils.urlEncode(new SimpleDateFormat("YYYY-MM-dd HH:mm").format(weekDate));
            } catch (Exception e) {
                Log.error(e);

                throw new RuntimeException("Invalid start time!");
            }

            try {
                endTime = CommonUtils.urlEncode(new SimpleDateFormat("YYYY-MM-dd HH:mm").format(nowDate));
            } catch (Exception e) {
                Log.error(e);

                throw new RuntimeException("Invalid end time!");
            }

        }

        monitorName = System.getProperty("monitor_name");
    }

    public void runTest() {
        // create session, positive test case
        String sessionID = createSession(userName, password);

        if(StringUtils.isNotEmpty(sessionID)) {
            try {
                HashMap<Integer, String> projects = getProjects(sessionID);

                for (Integer projectId: projects.keySet()) {
                    String url = accessServerURL + "/resource/monitor/" + sessionID + "/get-monitor-results?startDate="
                            + startTime + "&endDate=" + endTime + "&projectId=" + projectId;

                    if (StringUtils.isNotBlank(monitorName))
                        url += "&monitorName=" + monitorName;

                    String method = "GET";
                    String contentType = "*/*";
                    String acceptType = "*/*";
                    String body = "";

                    String data = RestUtils.restRequest(url,method,  contentType, acceptType, body);

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    MonitorResultResponse monitorResultResponse = mapper.readValue(data, MonitorResultResponse.class);

                    MonitorResult [] results = monitorResultResponse.getMonitorResults();

                    url = accessServerURL + "/resource/monitor/" + sessionID + "/get-monitor-result-incidents?startDate="
                            + startTime + "&endDate=" + endTime + "&projectId=" + projectId;

                    if (StringUtils.isNotBlank(monitorName))
                        url += "&monitorName=" + monitorName;

                    data = RestUtils.restRequest(url,method,  contentType, acceptType, body);

                    mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    MonitorResultIndicatorResponse monitorResultIncidentResponse = mapper.readValue(data, MonitorResultIndicatorResponse.class);

                    MonitorResultIndicator [] monitorResultIndicators = monitorResultIncidentResponse.getMonitorResultIndicators();

                    Log.debug2("MonitorResult Indicator ", "Data Received");

                    url = accessServerURL + "/resource/monitor/" + sessionID + "/get-monitor-result-indicators?startDate="
                            + startTime + "&endDate=" + endTime + "&projectId=" + projectId;

                    if (StringUtils.isNotBlank(monitorName))
                        url += "&monitorName=" + monitorName;

                    data = RestUtils.restRequest(url,method,  contentType, acceptType, body);
                }
            } catch(Exception e){
                Log.error(e);
            } finally {
                logoutSession(sessionID);
            }
        } else {
            Log.error("Unable to establish session - exiting!");
        }
    }

    private String logoutSession(String sessionID)
    {
        try {
            String url = accessServerURL + "/resource/portal/logout-api-session";
            String method = "POST";
            String contentType = "application/json";
            String acceptType = "application/json";

            StringWriter writer = createSessionParams(sessionID);

            return RestUtils.restRequest(url, method, contentType, acceptType, writer.toString());
        } catch(Exception e1) {
            Log.error(e1);
        }

        return null;
    }

    private HashMap<Integer, String> getProjects(String sessionID) {
        HashMap<Integer, String> allProjects = new HashMap<Integer, String>();

        try {
            String url = accessServerURL + "/resource/monitor/" + sessionID + "/get-projects";
            String method = "GET";
            String contentType = "*/*";
            String acceptType = "*/*";
            String body = "";

            String data = RestUtils.restRequest(url, method,  contentType, acceptType, body);

            ObjectMapper objectMapper = new ObjectMapper();

            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            final ProjectResponse projectResponse = objectMapper.readValue(data, ProjectResponse.class);

            if(projectResponse.getStatus().getStatusCode().equalsIgnoreCase("Success")) {
                Project[] projects = projectResponse.getProjects();

                for(int i = 0; i < projects.length; ++i) {
                    allProjects.put(projects[i].getId(), projects[i].getName());
                }
            }
        } catch(Exception e){
            Log.error(e);
        }

        return allProjects;
    }

    private String createSession(String email, String password)
    {
        try {
            String data = createSessionParams(email, password);

            JSONObject jsonObject;
            if(data != null) {
                jsonObject = getJsonObject(data);

                String status = (String)jsonObject.get("status");

                Assert.assertEquals("Status should not be failure", "SUCCESS", status.trim());

                String sessionID = (String)jsonObject.get("sessionID");

                return sessionID;
            }
        } catch(Exception e1) {
            Log.error(e1);
        }

        return null;
    }

    private String createSessionParams(String email, String password) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("email", email);
        jsonObject.put("password", password);

        StringWriter writer = new StringWriter();
        jsonObject.write(writer);

        String url = accessServerURL + "/resource/portal/establish-api-session";
        String method = "POST";
        String contentType = "application/json";
        String acceptType = "application/json";
        String body = writer.toString();

        return RestUtils.restRequest(url, method, contentType, acceptType, body);
    }

    protected JSONObject getJsonObject(String data) throws IOException, JSONException {
        return new JSONObject(data);
    }

    private StringBuffer getStringBuffer(InputStream inputStream) throws IOException {
        byte [] returnBytes = new byte[1024];

        StringBuffer buffer = new StringBuffer();

        int length = 0;

        do {
            length = inputStream.read(returnBytes, 0, returnBytes.length);

            if (length > 0) {
                String returnMsg = new String(returnBytes, 0, length, "UTF-8");

                buffer.append(returnMsg);
            }
        } while (length > 0);

        buffer.trimToSize();

        return buffer;
    }

    public StringWriter createSessionParams(String sessionID) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("sessionID", sessionID);

        StringWriter writer = new StringWriter();

        jsonObject.write(writer);

        return writer;
    }
}

class Project {
    private int     id;
    private String  name;
    private String  description;
    private int     createdByMuserId;
    private int     customerId;
    private boolean shared;
    private boolean managedExternally;
    private int     owningUserId;
    private int     owningUserGroupId;
    private String  shareCode;
    private boolean cleanup;
    private Date createdAt;
    private Date    lastModified;

    public  int     getId() {
        return id;
    }

    public  void    setId(int id) {
        this.id = id;
    }

    public  String  getName() {
        return name;
    }

    public  void    setName(String name) {
        this.name = name;
    }

    public  String  getDescription() {
        return description;
    }

    public  void    setDescription(String description) {
        this.description = description;
    }

    public  int     getCreatedByMuserId() {
        return createdByMuserId;
    }

    public  void    setCreatedByMuserId(int createdByMuserId) {
        this.createdByMuserId = createdByMuserId;
    }

    public  int     getCustomerId() {
        return customerId;
    }

    public  void    setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public  boolean     isShared() {
        return shared;
    }

    public  void    setShared(boolean shared) {
        this.shared = shared;
    }

    public  boolean     isManagedExternally() {
        return managedExternally;
    }

    public  void    setManagedExternally(boolean managedExternally) {
        this.managedExternally = managedExternally;
    }

    public  int     getOwningUserId() {
        return owningUserId;
    }

    public  void    setOwningUserId(int owningUserId) {
        this.owningUserId = owningUserId;
    }

    public  int     getOwningUserGroupId() {
        return owningUserGroupId;
    }

    public  void    setOwningUserGroupId(int owningUserGroupId) {
        this.owningUserGroupId = owningUserGroupId;
    }

    public  String  getShareCode() {
        return shareCode;
    }

    public  void    setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public  boolean     isCleanup() {
        return cleanup;
    }

    public  void    setCleanup(boolean cleanup) {
        this.cleanup = cleanup;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
}

class ProjectResponse {
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Project[] getProjects() {
        return projects;
    }

    public void setProjects(Project[] projects) {
        this.projects = projects;
    }

    private Status status;
    private String reason;
    private String failureReason;
    private Project [] projects;
}

class MonitorResult {
    private int id;
    private int customerId;
    private String monitorName;
    private Date startTime;
    private String device;
    private String location;
    private String carrier;
    private long totalRunTime;
    private boolean resultData;
    private int countSuccess;
    private int countFailure;
    private int countSLA;
    private int countException;
    private String deviceIds;
    private String deviceNames;
    private String deviceCarriers;
    private String deviceLocations;
    private String descriptionOfResult;
    private String scriptReturnCode;
    private boolean validResult;
    private boolean excluded;
    private String datasetValue;
    private int testScheduleId;
    private boolean transactionFailed;
    private String deviceMCDs;
    private String deviceSlots;
    private int projectId;
    private String errorCategory;
    private String errorType;
    private int runMode;
    private int monitorId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public long getTotalRunTime() {
        return totalRunTime;
    }

    public void setTotalRunTime(long totalRunTime) {
        this.totalRunTime = totalRunTime;
    }

    public boolean isResultData() {
        return resultData;
    }

    public void setResultData(boolean resultData) {
        this.resultData = resultData;
    }

    public int getCountSuccess() {
        return countSuccess;
    }

    public void setCountSuccess(int countSuccess) {
        this.countSuccess = countSuccess;
    }

    public int getCountFailure() {
        return countFailure;
    }

    public void setCountFailure(int countFailure) {
        this.countFailure = countFailure;
    }

    public int getCountSLA() {
        return countSLA;
    }

    public void setCountSLA(int countSLA) {
        this.countSLA = countSLA;
    }

    public int getCountException() {
        return countException;
    }

    public void setCountException(int countException) {
        this.countException = countException;
    }

    public String getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(String deviceIds) {
        this.deviceIds = deviceIds;
    }

    public String getDeviceNames() {
        return deviceNames;
    }

    public void setDeviceNames(String deviceNames) {
        this.deviceNames = deviceNames;
    }

    public String getDeviceCarriers() {
        return deviceCarriers;
    }

    public void setDeviceCarriers(String deviceCarriers) {
        this.deviceCarriers = deviceCarriers;
    }

    public String getDeviceLocations() {
        return deviceLocations;
    }

    public void setDeviceLocations(String deviceLocations) {
        this.deviceLocations = deviceLocations;
    }

    public String getDescriptionOfResult() {
        return descriptionOfResult;
    }

    public void setDescriptionOfResult(String descriptionOfResult) {
        this.descriptionOfResult = descriptionOfResult;
    }

    public String getScriptReturnCode() {
        return scriptReturnCode;
    }

    public void setScriptReturnCode(String scriptReturnCode) {
        this.scriptReturnCode = scriptReturnCode;
    }

    public boolean isValidResult() {
        return validResult;
    }

    public void setValidResult(boolean validResult) {
        this.validResult = validResult;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public String getDatasetValue() {
        return datasetValue;
    }

    public void setDatasetValue(String datasetValue) {
        this.datasetValue = datasetValue;
    }

    public int getTestScheduleId() {
        return testScheduleId;
    }

    public void setTestScheduleId(int testScheduleId) {
        this.testScheduleId = testScheduleId;
    }

    public boolean isTransactionFailed() {
        return transactionFailed;
    }

    public void setTransactionFailed(boolean transactionFailed) {
        this.transactionFailed = transactionFailed;
    }

    public String getDeviceMCDs() {
        return deviceMCDs;
    }

    public void setDeviceMCDs(String deviceMCDs) {
        this.deviceMCDs = deviceMCDs;
    }

    public String getDeviceSlots() {
        return deviceSlots;
    }

    public void setDeviceSlots(String deviceSlots) {
        this.deviceSlots = deviceSlots;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public int getRunMode() {
        return runMode;
    }

    public void setRunMode(int runMode) {
        this.runMode = runMode;
    }

    public int getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(int monitorId) {
        this.monitorId = monitorId;
    }
}

class MonitorResultResponse {
    private Status status;
    private String reason;
    private String failureReason;
    private MonitorResult [] monitorResults;

    public MonitorResultResponse() {
        super();
        monitorResults = new MonitorResult[0];
    }

    public MonitorResultResponse(Status status, String reason) {
        monitorResults = new MonitorResult[0];
    }

    public MonitorResultResponse(Status status, String reason, MonitorResult [] monitorResults) {
        this.monitorResults = monitorResults;
    }

    public MonitorResult[] getMonitorResults() {
        return monitorResults;
    }

    public void setMonitorResults(MonitorResult[] monitorResults) {
        this.monitorResults = monitorResults;
    }
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

}

class MonitorResultIncidentResponse {
    private Status status;
    private String reason;
    private String failureReason;
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    private MonitorResultIncident [] monitorResultIncidents;

    public MonitorResultIncidentResponse() {
        super();
        monitorResultIncidents = new MonitorResultIncident[0];
    }

    public MonitorResultIncidentResponse(Status status, String reason) {
        monitorResultIncidents = new MonitorResultIncident[0];
    }

    public MonitorResultIncidentResponse(Status status, String reason, MonitorResultIncident [] monitorResultIncidents) {
        this.monitorResultIncidents = monitorResultIncidents;
    }

    public MonitorResultIncident[] getMonitorResultIncidents() {
        return monitorResultIncidents;
    }

    public void setMonitorResultIncidents(MonitorResultIncident[] monitorResultIncidents) {
        this.monitorResultIncidents = monitorResultIncidents;
    }
}

class MonitorResultIncident {
    private String id;
    private int customerId;
    private int projectId;
    private int testScheduleId;
    private String monitorName;
    private String policyName;
    private String policyType;
    private String policyDescription;
    private int startMonitorResultId;
    private int endMonitorResultId;
    private Date startTime;
    private Date endTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getTestScheduleId() {
        return testScheduleId;
    }

    public void setTestScheduleId(int testScheduleId) {
        this.testScheduleId = testScheduleId;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public String getPolicyDescription() {
        return policyDescription;
    }

    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
    }

    public int getStartMonitorResultId() {
        return startMonitorResultId;
    }

    public void setStartMonitorResultId(int startMonitorResultId) {
        this.startMonitorResultId = startMonitorResultId;
    }

    public int getEndMonitorResultId() {
        return endMonitorResultId;
    }

    public void setEndMonitorResultId(int endMonitorResultId) {
        this.endMonitorResultId = endMonitorResultId;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
}

class MonitorResultIndicatorCustomVar {
    private String variableName;
    private String variableValue;
    private boolean plottable;

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableValue() {
        return variableValue;
    }

    public void setVariableValue(String variableValue) {
        this.variableValue = variableValue;
    }

    public boolean isPlottable() {
        return plottable;
    }

    public void setPlottable(boolean plottable) {
        this.plottable = plottable;
    }
}

class MonitorResultIndicator {
    private int id;
    private int customerId;
    private int monitorResultId;
    private Date startTime;
    private String indicatorType;
    private double range;
    private String units;
    private double value;
    private boolean validResult;
    private int thresholdValue;
    private int thresholdConditionId;
    private boolean thresholdBreached;
    private boolean transactionFailed;
    private String monitorName;
    private int projectId;
    private String indicatorGroup;
    private String indicatorName;
    private String errorCode;
    private String errorDetails;
    private MonitorResultIndicatorCustomVar [] monitorResultIndicatorCustomVars;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getMonitorResultId() {
        return monitorResultId;
    }

    public void setMonitorResultId(int monitorResultId) {
        this.monitorResultId = monitorResultId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getIndicatorType() {
        return indicatorType;
    }

    public void setIndicatorType(String indicatorType) {
        this.indicatorType = indicatorType;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public boolean isValidResult() {
        return validResult;
    }

    public void setValidResult(boolean validResult) {
        this.validResult = validResult;
    }

    public int getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(int thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public int getThresholdConditionId() {
        return thresholdConditionId;
    }

    public void setThresholdConditionId(int thresholdConditionId) {
        this.thresholdConditionId = thresholdConditionId;
    }

    public boolean isThresholdBreached() {
        return thresholdBreached;
    }

    public void setThresholdBreached(boolean thresholdBreached) {
        this.thresholdBreached = thresholdBreached;
    }

    public boolean isTransactionFailed() {
        return transactionFailed;
    }

    public void setTransactionFailed(boolean transactionFailed) {
        this.transactionFailed = transactionFailed;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getIndicatorGroup() {
        return indicatorGroup;
    }

    public void setIndicatorGroup(String indicatorGroup) {
        this.indicatorGroup = indicatorGroup;
    }

    public String getIndicatorName() {
        return indicatorName;
    }

    public void setIndicatorName(String indicatorName) {
        this.indicatorName = indicatorName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public MonitorResultIndicatorCustomVar[] getMonitorResultIndicatorCustomVars() {
        return monitorResultIndicatorCustomVars;
    }

    public void setMonitorResultIndicatorCustomVars(MonitorResultIndicatorCustomVar[] monitorResultIndicatorCustomVars) {
        this.monitorResultIndicatorCustomVars = monitorResultIndicatorCustomVars;
    }
}

class MonitorResultIndicatorResponse  {
    private Status status;
    private String reason;
    private String failureReason;
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    private MonitorResultIndicator [] monitorResultIndicators;

    public MonitorResultIndicatorResponse() {
        super();

        monitorResultIndicators = new MonitorResultIndicator[0];
    }

    public MonitorResultIndicatorResponse(Status status, String reason) {

        monitorResultIndicators = new MonitorResultIndicator[0];
    }

    public MonitorResultIndicatorResponse(Status status, String reason, MonitorResultIndicator [] monitorResultIndicators) {

        this.monitorResultIndicators = monitorResultIndicators;
    }

    public MonitorResultIndicator[] getMonitorResultIndicators() {
        return monitorResultIndicators;
    }

    public void setMonitorResultIndicators(MonitorResultIndicator[] monitorResultIndicators) {
        this.monitorResultIndicators = monitorResultIndicators;
    }
}
