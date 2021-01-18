/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.kie.server.springboot.samples;

import static org.appformer.maven.integration.MavenRepository.getMavenRepository;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.appformer.maven.integration.MavenRepository;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieServices;
import org.kie.server.api.KieServerConstants;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieServerMode;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.kie.server.springboot.samples.utils.KeycloakContainer;
import org.kie.server.springboot.samples.utils.KeycloakFixture;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {KieServerApplication.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations="classpath:application-test.properties")
public class KeycloakKieServerTest {

    static final String ARTIFACT_ID = "evaluation";
    static final String GROUP_ID = "org.jbpm.test";
    static final String VERSION = "1.0.0";
    
    @LocalServerPort
    private int port;    
   
    private static String user = "john";
    private static String password = "john1";

    private String containerId = "evaluation";
    private String processId = "evaluation";
    
    private KieServicesClient kieServicesClient;
    
    private static KeycloakContainer keycloak = new KeycloakContainer();
    
    @BeforeClass
    public static void generalSetup() {
        setUpKeycloakTestContainers();

        System.setProperty(KieServerConstants.KIE_SERVER_MODE, KieServerMode.PRODUCTION.name());
        KieServices ks = KieServices.Factory.get();
        org.kie.api.builder.ReleaseId releaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        File kjar = new File("../kjars/evaluation/jbpm-module.jar");
        File pom = new File("../kjars/evaluation/pom.xml");
        MavenRepository repository = getMavenRepository();
        repository.installArtifact(releaseId, kjar, pom);
    }

    public static void setUpKeycloakTestContainers() {
        // Currently testcontainers are not supported out-of-the-box on Windows and RHEL8
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win") 
                && !System.getProperty("os.version").toLowerCase().contains("el8"));
        
        keycloak.start();
        KeycloakFixture.setup(keycloak.getAuthServerUrl(), user, password);
    }

    @DynamicPropertySource
    public static void registerKeycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("keycloak.auth-server-url", () -> keycloak.getAuthServerUrl());
    }

    @AfterClass
    public static void generalCleanup() {
        keycloak.stop();
        System.clearProperty(KieServerConstants.KIE_SERVER_MODE);
    }

    @Before
    public void setup() {
        ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        String serverUrl = "http://localhost:" + port + "/rest/server";
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl, user, password);
        configuration.setTimeout(60000);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        this.kieServicesClient =  KieServicesFactory.newKieServicesClient(configuration);
        
        KieContainerResource resource = new KieContainerResource(containerId, releaseId);
        kieServicesClient.createContainer(containerId, resource);
    }
    
    @After
    public void cleanup() {
        kieServicesClient.disposeContainer(containerId);        
    }
    
    @Test
    public void testProcessStartAndAbort() {

        // query for all available process definitions
        QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        List<ProcessDefinition> processes = queryClient.findProcesses(0, 10);
        assertEquals(1, processes.size());

        ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        // get details of process definition
        ProcessDefinition definition = processClient.getProcessDefinition(containerId, processId);
        assertNotNull(definition);
        assertEquals(processId, definition.getId());

        // start process instance
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "john");
        params.put("reason", "test on spring boot");
        Long processInstanceId = processClient.startProcess(containerId, processId, params);
        assertNotNull(processInstanceId);
       
        // find active process instances
        List<ProcessInstance> instances = queryClient.findProcessInstances(0, 10);
        assertEquals(1, instances.size());

        // at the end abort process instance
        processClient.abortProcessInstance(containerId, processInstanceId);

        ProcessInstance processInstance = queryClient.findProcessInstanceById(processInstanceId);
        assertNotNull(processInstance);
        assertEquals(3, processInstance.getState().intValue());        
    }

    @Test
    public void testProcessStartAndWorkOnUserTask() {

        // query for all available process definitions
        QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        List<ProcessDefinition> processes = queryClient.findProcesses(0, 10);
        assertEquals(1, processes.size());

        ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        // get details of process definition
        ProcessDefinition definition = processClient.getProcessDefinition(containerId, processId);
        assertNotNull(definition);
        assertEquals(processId, definition.getId());

        // start process instance
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "john");
        params.put("reason", "test on spring boot");
        Long processInstanceId = processClient.startProcess(containerId, processId, params);
        assertNotNull(processInstanceId);
       
        UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
        // find available tasks
        List<TaskSummary> tasks = taskClient.findTasksAssignedAsPotentialOwner(user, 0, 10);
        assertEquals(1, tasks.size());

        // complete task
        Long taskId = tasks.get(0).getId();

        taskClient.startTask(containerId, taskId, user);
        taskClient.completeTask(containerId, taskId, user, null);

        // find active process instances
        List<ProcessInstance> instances = queryClient.findProcessInstances(0, 10);
        assertEquals(1, instances.size());
        
        tasks = taskClient.findTasksAssignedAsPotentialOwner(user, 0, 10);
        assertEquals(1, tasks.size());

        // at the end abort process instance
        processClient.abortProcessInstance(containerId, processInstanceId);

        ProcessInstance processInstance = queryClient.findProcessInstanceById(processInstanceId);
        assertNotNull(processInstance);
        assertEquals(3, processInstance.getState().intValue());        
    }
}