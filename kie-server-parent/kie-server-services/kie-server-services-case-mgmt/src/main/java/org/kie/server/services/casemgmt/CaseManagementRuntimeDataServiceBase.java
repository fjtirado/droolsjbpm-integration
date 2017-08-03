/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.server.services.casemgmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jbpm.casemgmt.api.CaseRuntimeDataService;
import org.jbpm.casemgmt.api.model.AdHocFragment;
import org.jbpm.casemgmt.api.model.CaseDefinition;
import org.jbpm.casemgmt.api.model.CaseFileItem;
import org.jbpm.casemgmt.api.model.CaseStatus;
import org.jbpm.casemgmt.api.model.instance.CaseMilestoneInstance;
import org.jbpm.casemgmt.api.model.instance.CaseStageInstance;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.identity.IdentityProvider;
import org.kie.server.api.KieServerConstants;
import org.kie.server.api.model.cases.CaseAdHocFragment;
import org.kie.server.api.model.cases.CaseAdHocFragmentList;
import org.kie.server.api.model.cases.CaseDefinitionList;
import org.kie.server.api.model.cases.CaseFileDataItemList;
import org.kie.server.api.model.cases.CaseInstance;
import org.kie.server.api.model.cases.CaseInstanceList;
import org.kie.server.api.model.cases.CaseMilestone;
import org.kie.server.api.model.cases.CaseMilestoneList;
import org.kie.server.api.model.cases.CaseStage;
import org.kie.server.api.model.cases.CaseStageList;
import org.kie.server.api.model.definition.ProcessDefinitionList;
import org.kie.server.api.model.instance.NodeInstance;
import org.kie.server.api.model.instance.NodeInstanceList;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.ProcessInstanceList;
import org.kie.server.api.model.instance.TaskSummaryList;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.impl.locator.ContainerLocatorProvider;

import static java.util.stream.Collectors.*;

public class CaseManagementRuntimeDataServiceBase {

    private CaseRuntimeDataService caseRuntimeDataService;
    private KieServerRegistry context;
    private IdentityProvider identityProvider;

    private boolean bypassAuthUser = false;

    public CaseManagementRuntimeDataServiceBase(CaseRuntimeDataService caseRuntimeDataService, KieServerRegistry context) {
        this.caseRuntimeDataService = caseRuntimeDataService;
        this.identityProvider = context.getIdentityProvider();
        this.context = context;

        this.bypassAuthUser = Boolean.parseBoolean(context.getConfig().getConfigItemValue(KieServerConstants.CFG_BYPASS_AUTH_USER, "false"));
    }

    protected String getUser(String queryParamUser) {
        if (bypassAuthUser) {
            return queryParamUser;
        }

        return identityProvider.getName();
    }

    public CaseMilestoneList getMilestones(String containerId, String caseId, boolean achievedOnly, Integer page, Integer pageSize) {

        Collection<CaseMilestoneInstance> caseMilestones = caseRuntimeDataService.getCaseInstanceMilestones(caseId, achievedOnly, ConvertUtils.buildQueryContext(page, pageSize));

        List<CaseMilestone> milestones = ConvertUtils.transformMilestones(caseMilestones);

        CaseMilestoneList milestoneList = new CaseMilestoneList(milestones);

        return milestoneList;
    }

    public CaseStageList getStages(String containerId, String caseId, boolean activeOnly, Integer page, Integer pageSize) {

        Collection<CaseStageInstance> caseStageInstances = caseRuntimeDataService.getCaseInstanceStages(caseId, activeOnly, ConvertUtils.buildQueryContext(page, pageSize));

        List<CaseStage> caseStages = ConvertUtils.transformStages(caseStageInstances);

        CaseStageList stageList = new CaseStageList(caseStages);

        return stageList;
    }

    public CaseAdHocFragmentList getAdHocFragments(String containerId, String caseId) {

        Collection<AdHocFragment> caseAdHocFragments = caseRuntimeDataService.getAdHocFragmentsForCase(caseId);

        List<CaseAdHocFragment> caseAdHoc = caseAdHocFragments.stream().map(adf -> CaseAdHocFragment.builder().name(adf.getName()).type(adf.getType()).build()).collect(toList());

        CaseAdHocFragmentList adHocFragmentList = new CaseAdHocFragmentList(caseAdHoc);

        return adHocFragmentList;
    }

    public NodeInstanceList getActiveNodes(String containerId, String caseId, Integer page, Integer pageSize) {

        Collection<NodeInstanceDesc> activeNodeInstances = caseRuntimeDataService.getActiveNodesForCase(caseId, ConvertUtils.buildQueryContext(page, pageSize));

        List<NodeInstance> activeNodes = ConvertUtils.transformNodeInstance(activeNodeInstances);

        NodeInstanceList activeNodesList = new NodeInstanceList(activeNodes);

        return activeNodesList;
    }

    public NodeInstanceList getCompletedNodes(String containerId, String caseId, Integer page, Integer pageSize) {

        Collection<NodeInstanceDesc> completedNodeInstances = caseRuntimeDataService.getCompletedNodesForCase(caseId, ConvertUtils.buildQueryContext(page, pageSize));

        List<NodeInstance> completedNodes = ConvertUtils.transformNodeInstance(completedNodeInstances);

        NodeInstanceList completedNodesList = new NodeInstanceList(completedNodes);

        return completedNodesList;
    }

    public ProcessInstanceList getProcessInstancesForCase(String containerId, String caseId, List<Integer> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        status = safeProcessStatus(status);
        sort = safeProcessInstanceSort(sort);

        Collection<ProcessInstanceDesc> processInstanceDescs = caseRuntimeDataService.getProcessInstancesForCase(caseId, status, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<ProcessInstance> processInstances = ConvertUtils.transformProcessInstance(processInstanceDescs);

        ProcessInstanceList processInstancesList = new ProcessInstanceList(processInstances);

        return processInstancesList;
    }

    public CaseInstanceList getCaseInstancesByContainer(String containerId, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstancesByDeployment(containerId, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstancesByDefinition(String containerId, String caseDefinitionId, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstancesByDefinition(caseDefinitionId, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstancesOwnedBy(String owner, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        owner = getUser(owner);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstancesOwnedBy(owner, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstances(List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstances(caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstancesByRole(String roleName, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstancesByRole(roleName, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstancesAnyRole(List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = caseRuntimeDataService.getCaseInstancesAnyRole(caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseInstanceList getCaseInstancesByCaseFileData(String dataItemName, String dataItemValue, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<CaseStatus> caseStatus = safeCaseStatus(status);
        sort = safeCaseInstanceSort(sort);

        Collection<org.jbpm.casemgmt.api.model.instance.CaseInstance> caseInstanceDescs = null;
        if (dataItemValue != null && !dataItemValue.isEmpty()) {
            caseInstanceDescs = caseRuntimeDataService.getCaseInstancesByDateItemAndValue(dataItemName, dataItemValue, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        } else {
            caseInstanceDescs = caseRuntimeDataService.getCaseInstancesByDateItem(dataItemName, caseStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        }

        List<CaseInstance> caseInstances = ConvertUtils.transformCaseInstances(caseInstanceDescs);

        CaseInstanceList caseInstancesList = new CaseInstanceList(caseInstances);

        return caseInstancesList;
    }

    public CaseDefinitionList getCaseDefinitionsByContainer(String containerId, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        sort = safeCaseDefinitionSort(sort);
        containerId = context.getContainerId(containerId, ContainerLocatorProvider.get().getLocator());

        Collection<CaseDefinition> caseDescs = caseRuntimeDataService.getCasesByDeployment(containerId, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        List<org.kie.server.api.model.cases.CaseDefinition> cases = ConvertUtils.transformCases(caseDescs);

        CaseDefinitionList caseList = new CaseDefinitionList(cases);

        return caseList;
    }

    public CaseDefinitionList getCaseDefinitions(String filter, Integer page, Integer pageSize, String sort, boolean sortOrder) {

        Collection<CaseDefinition> caseDescs = null;
        sort = safeCaseDefinitionSort(sort);

        if (filter != null && !filter.isEmpty()) {
            caseDescs = caseRuntimeDataService.getCases(filter, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        } else {
            caseDescs = caseRuntimeDataService.getCases(ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        }

        List<org.kie.server.api.model.cases.CaseDefinition> cases = ConvertUtils.transformCases(caseDescs);

        CaseDefinitionList caseList = new CaseDefinitionList(cases);

        return caseList;
    }

    public ProcessDefinitionList getProcessDefinitions(String filter, String containerId, Integer page, Integer pageSize, String sort, boolean sortOrder) {

        Collection<org.jbpm.services.api.model.ProcessDefinition> processDescs = null;
        sort = safeCaseDefinitionSort(sort);

        if (containerId != null && !containerId.isEmpty()) {
            containerId = context.getContainerId(containerId, ContainerLocatorProvider.get().getLocator());
            processDescs = caseRuntimeDataService.getProcessDefinitionsByDeployment(containerId, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        } else if (filter != null && !filter.isEmpty()) {
            processDescs = caseRuntimeDataService.getProcessDefinitions(filter, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        } else {
            processDescs = caseRuntimeDataService.getProcessDefinitions(ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));
        }

        ProcessDefinitionList processDefinitions = ConvertUtils.transformProcesses(processDescs);

        return processDefinitions;
    }

    public org.kie.server.api.model.cases.CaseDefinition getCaseDefinition(String containerId, String caseDefinitionId) {
        containerId = context.getContainerId(containerId, ContainerLocatorProvider.get().getLocator());
        CaseDefinition caseDef = caseRuntimeDataService.getCase(containerId, caseDefinitionId);

        if (caseDef == null) {
            throw new IllegalStateException("Case definition " + containerId + " : " + caseDefinitionId + " not found");
        }

        org.kie.server.api.model.cases.CaseDefinition caseDefinition = ConvertUtils.transformCase(caseDef);

        return caseDefinition;
    }

    public TaskSummaryList getCaseTasks(String caseId, String user, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<Status> actualStatus = safeTaskStatus(status);
        user = getUser(user);
        sort = safeTaskSummarySort(sort);

        Collection<TaskSummary> taskSummaries = caseRuntimeDataService.getCaseTasksAssignedAsPotentialOwner(caseId, user, actualStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        TaskSummaryList tasks = ConvertUtils.convertToTaskSummaryList(taskSummaries);

        return tasks;
    }

    public TaskSummaryList getCaseTasksAsBusinessAdmin(String caseId, String user, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<Status> actualStatus = safeTaskStatus(status);
        user = getUser(user);
        sort = safeTaskSummarySort(sort);

        Collection<TaskSummary> taskSummaries = caseRuntimeDataService.getCaseTasksAssignedAsBusinessAdmin(caseId, user, actualStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        TaskSummaryList tasks = ConvertUtils.convertToTaskSummaryList(taskSummaries);

        return tasks;
    }

    public TaskSummaryList getCaseTasksAsStakeholder(String caseId, String user, List<String> status, Integer page, Integer pageSize, String sort, boolean sortOrder) {
        List<Status> actualStatus = safeTaskStatus(status);
        user = getUser(user);
        sort = safeTaskSummarySort(sort);

        Collection<TaskSummary> taskSummaries = caseRuntimeDataService.getCaseTasksAssignedAsStakeholder(caseId, user, actualStatus, ConvertUtils.buildQueryContext(page, pageSize, sort, sortOrder));

        TaskSummaryList tasks = ConvertUtils.convertToTaskSummaryList(taskSummaries);

        return tasks;
    }

    public CaseFileDataItemList getCaseInstanceDataItems(String caseId, List<String> names, List<String> types, Integer page, Integer pageSize) {
        Collection<CaseFileItem> caseFileItems = null;
        if (!names.isEmpty()) {
            caseFileItems = caseRuntimeDataService.getCaseInstanceDataItemsByName(caseId, names, ConvertUtils.buildQueryContext(page, pageSize));
        } else if (!types.isEmpty()) {
            caseFileItems = caseRuntimeDataService.getCaseInstanceDataItemsByType(caseId, types, ConvertUtils.buildQueryContext(page, pageSize));
        } else {
            caseFileItems = caseRuntimeDataService.getCaseInstanceDataItems(caseId, ConvertUtils.buildQueryContext(page, pageSize));
        }

        CaseFileDataItemList caseFileDataItemList = ConvertUtils.transformCaseFileDataItems(caseFileItems);
        return caseFileDataItemList;
    }

    protected List<CaseStatus> safeCaseStatus(List<String> status) {
        List<CaseStatus> actualStatus = CaseStatus.fromNameList(status);
        if (actualStatus == null || actualStatus.isEmpty()) {
            actualStatus = new ArrayList<CaseStatus>();
            actualStatus.add(CaseStatus.OPEN);
        }

        return actualStatus;
    }

    protected List<Integer> safeProcessStatus(List<Integer> status) {
        List<Integer> actualStatus = status;
        if (status == null || status.isEmpty()) {
            actualStatus = new ArrayList<Integer>();
            actualStatus.add(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE);
        }

        return actualStatus;
    }

    protected List<Status> safeTaskStatus(List<String> status) {
        List<Status> actualStatus;
        if (status == null || status.isEmpty()) {
            actualStatus = new ArrayList<Status>();
            actualStatus.add(Status.Ready);
            actualStatus.add(Status.Reserved);
            actualStatus.add(Status.InProgress);
        } else {
            actualStatus = status.stream().map(s -> Status.valueOf(s)).collect(toList());
        }

        return actualStatus;
    }

    protected String safeCaseInstanceSort(String sort) {
        String actualSort = sort;
        if (sort == null || sort.isEmpty()) {
            actualSort = "CorrelationKey";
        }

        return actualSort;
    }

    protected String safeTaskSummarySort(String sort) {
        String actualSort = sort;
        if (sort == null || sort.isEmpty()) {
            actualSort = "t.id";
        }

        return actualSort;
    }

    protected String safeCaseDefinitionSort(String sort) {
        String actualSort = sort;
        if (sort == null || sort.isEmpty()) {
            actualSort = "CaseId";
        }

        return actualSort;
    }

    protected String safeProcessInstanceSort(String sort) {
        String actualSort = sort;
        if (sort == null || sort.isEmpty()) {
            actualSort = "ProcessInstanceId";
        }

        return actualSort;
    }
}