package com.ruoyi.project.langchain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LangGraph StateGraph 语义的工作流清单模型。
 *
 * <p>对应 langchain/rencai-huaxiang-*.langgraph.json，使用 snake_case 与 JSON 字段一一对应。</p>
 *
 * @author ruoyi
 */
public class LangChainWorkflow
{
    private String schemaVersion;

    private String framework;

    private String format;

    private Flow flow;

    private State state;

    private Map<String, ModelConfig> models;

    private Graph graph;

    private Execution execution;

    private Map<String, Object> extensions;

    public String getSchemaVersion()
    {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion)
    {
        this.schemaVersion = schemaVersion;
    }

    public String getFramework()
    {
        return framework;
    }

    public void setFramework(String framework)
    {
        this.framework = framework;
    }

    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }

    public Flow getFlow()
    {
        return flow;
    }

    public void setFlow(Flow flow)
    {
        this.flow = flow;
    }

    public State getState()
    {
        return state;
    }

    public void setState(State state)
    {
        this.state = state;
    }

    public Map<String, ModelConfig> getModels()
    {
        return models;
    }

    public void setModels(Map<String, ModelConfig> models)
    {
        this.models = models;
    }

    public Graph getGraph()
    {
        return graph;
    }

    public void setGraph(Graph graph)
    {
        this.graph = graph;
    }

    public Execution getExecution()
    {
        return execution;
    }

    public void setExecution(Execution execution)
    {
        this.execution = execution;
    }

    public Map<String, Object> getExtensions()
    {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions)
    {
        this.extensions = extensions;
    }

    public static class Flow
    {
        private String id;
        private String name;
        private String description;
        private Integer category;
        private String dslVersion;
        private String avatarIcon;
        private String avatarColor;
        private Map<String, Object> advancedConfig;
        private String advancedConfigRaw;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public Integer getCategory()
        {
            return category;
        }

        public void setCategory(Integer category)
        {
            this.category = category;
        }

        public String getDslVersion()
        {
            return dslVersion;
        }

        public void setDslVersion(String dslVersion)
        {
            this.dslVersion = dslVersion;
        }

        public String getAvatarIcon()
        {
            return avatarIcon;
        }

        public void setAvatarIcon(String avatarIcon)
        {
            this.avatarIcon = avatarIcon;
        }

        public String getAvatarColor()
        {
            return avatarColor;
        }

        public void setAvatarColor(String avatarColor)
        {
            this.avatarColor = avatarColor;
        }

        public Map<String, Object> getAdvancedConfig()
        {
            return advancedConfig;
        }

        public void setAdvancedConfig(Map<String, Object> advancedConfig)
        {
            this.advancedConfig = advancedConfig;
        }

        public String getAdvancedConfigRaw()
        {
            return advancedConfigRaw;
        }

        public void setAdvancedConfigRaw(String advancedConfigRaw)
        {
            this.advancedConfigRaw = advancedConfigRaw;
        }
    }

    public static class State
    {
        private Map<String, StateField> schema;
        private Map<String, Object> defaults;

        public Map<String, StateField> getSchema()
        {
            return schema;
        }

        public void setSchema(Map<String, StateField> schema)
        {
            this.schema = schema;
        }

        public Map<String, Object> getDefaults()
        {
            return defaults;
        }

        public void setDefaults(Map<String, Object> defaults)
        {
            this.defaults = defaults;
        }
    }

    public static class StateField
    {
        private String type;

        @JsonProperty("default")
        private Object defaultValue;

        private Boolean required;
        private String producer;

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public Object getDefaultValue()
        {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue)
        {
            this.defaultValue = defaultValue;
        }

        public Boolean getRequired()
        {
            return required;
        }

        public void setRequired(Boolean required)
        {
            this.required = required;
        }

        public String getProducer()
        {
            return producer;
        }

        public void setProducer(String producer)
        {
            this.producer = producer;
        }
    }

    public static class ModelConfig
    {
        private String provider;
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Integer timeoutSeconds;
        private String apiKeyEnv;
        private Map<String, Object> providerOptions;

        public String getProvider()
        {
            return provider;
        }

        public void setProvider(String provider)
        {
            this.provider = provider;
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getModelName()
        {
            return modelName;
        }

        public void setModelName(String modelName)
        {
            this.modelName = modelName;
        }

        public Double getTemperature()
        {
            return temperature;
        }

        public void setTemperature(Double temperature)
        {
            this.temperature = temperature;
        }

        public Integer getTimeoutSeconds()
        {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds)
        {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getApiKeyEnv()
        {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv)
        {
            this.apiKeyEnv = apiKeyEnv;
        }

        public Map<String, Object> getProviderOptions()
        {
            return providerOptions;
        }

        public void setProviderOptions(Map<String, Object> providerOptions)
        {
            this.providerOptions = providerOptions;
        }
    }

    public static class Graph
    {
        private String entrypoint;
        private String finishPoint;
        private String entrypointRuntime;
        private String finishRuntime;
        private List<Node> nodes;
        private List<Edge> edges;
        private List<ConditionalEdge> conditionalEdges;

        public String getEntrypoint()
        {
            return entrypoint;
        }

        public void setEntrypoint(String entrypoint)
        {
            this.entrypoint = entrypoint;
        }

        public String getFinishPoint()
        {
            return finishPoint;
        }

        public void setFinishPoint(String finishPoint)
        {
            this.finishPoint = finishPoint;
        }

        public String getEntrypointRuntime()
        {
            return entrypointRuntime;
        }

        public void setEntrypointRuntime(String entrypointRuntime)
        {
            this.entrypointRuntime = entrypointRuntime;
        }

        public String getFinishRuntime()
        {
            return finishRuntime;
        }

        public void setFinishRuntime(String finishRuntime)
        {
            this.finishRuntime = finishRuntime;
        }

        public List<Node> getNodes()
        {
            return nodes;
        }

        public void setNodes(List<Node> nodes)
        {
            this.nodes = nodes;
        }

        public List<Edge> getEdges()
        {
            return edges;
        }

        public void setEdges(List<Edge> edges)
        {
            this.edges = edges;
        }

        public List<ConditionalEdge> getConditionalEdges()
        {
            return conditionalEdges;
        }

        public void setConditionalEdges(List<ConditionalEdge> conditionalEdges)
        {
            this.conditionalEdges = conditionalEdges;
        }
    }

    public static class Node
    {
        private String id;
        private String runtimeName;
        private String type;
        private String iflytekType;
        private String name;
        private String alias;
        private Map<String, Object> nodeMeta;
        private String description;
        private Map<String, Object> position;
        private Map<String, Object> positionAbsolute;
        private Map<String, Object> size;
        private String icon;
        private Map<String, Object> flags;
        private List<Object> references;
        private String status;
        private List<Port> inputs;
        private List<Port> outputs;
        private Map<String, Object> nodeParam;
        private Map<String, Object> stateWrites;
        private Code code;
        private Router router;
        private Llm llm;

        @JsonProperty("output")
        private End end;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getRuntimeName()
        {
            return runtimeName;
        }

        public void setRuntimeName(String runtimeName)
        {
            this.runtimeName = runtimeName;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getIflytekType()
        {
            return iflytekType;
        }

        public void setIflytekType(String iflytekType)
        {
            this.iflytekType = iflytekType;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getAlias()
        {
            return alias;
        }

        public void setAlias(String alias)
        {
            this.alias = alias;
        }

        public Map<String, Object> getNodeMeta()
        {
            return nodeMeta;
        }

        public void setNodeMeta(Map<String, Object> nodeMeta)
        {
            this.nodeMeta = nodeMeta;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public Map<String, Object> getPosition()
        {
            return position;
        }

        public void setPosition(Map<String, Object> position)
        {
            this.position = position;
        }

        public Map<String, Object> getPositionAbsolute()
        {
            return positionAbsolute;
        }

        public void setPositionAbsolute(Map<String, Object> positionAbsolute)
        {
            this.positionAbsolute = positionAbsolute;
        }

        public Map<String, Object> getSize()
        {
            return size;
        }

        public void setSize(Map<String, Object> size)
        {
            this.size = size;
        }

        public String getIcon()
        {
            return icon;
        }

        public void setIcon(String icon)
        {
            this.icon = icon;
        }

        public Map<String, Object> getFlags()
        {
            return flags;
        }

        public void setFlags(Map<String, Object> flags)
        {
            this.flags = flags;
        }

        public List<Object> getReferences()
        {
            return references;
        }

        public void setReferences(List<Object> references)
        {
            this.references = references;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public List<Port> getInputs()
        {
            return inputs;
        }

        public void setInputs(List<Port> inputs)
        {
            this.inputs = inputs;
        }

        public List<Port> getOutputs()
        {
            return outputs;
        }

        public void setOutputs(List<Port> outputs)
        {
            this.outputs = outputs;
        }

        public Map<String, Object> getNodeParam()
        {
            return nodeParam;
        }

        public void setNodeParam(Map<String, Object> nodeParam)
        {
            this.nodeParam = nodeParam;
        }

        public Map<String, Object> getStateWrites()
        {
            return stateWrites;
        }

        public void setStateWrites(Map<String, Object> stateWrites)
        {
            this.stateWrites = stateWrites;
        }

        public Code getCode()
        {
            return code;
        }

        public void setCode(Code code)
        {
            this.code = code;
        }

        public Router getRouter()
        {
            return router;
        }

        public void setRouter(Router router)
        {
            this.router = router;
        }

        public Llm getLlm()
        {
            return llm;
        }

        public void setLlm(Llm llm)
        {
            this.llm = llm;
        }

        public End getEnd()
        {
            return end;
        }

        public void setEnd(End end)
        {
            this.end = end;
        }
    }

    public static class Port
    {
        private String id;
        private String name;
        private String type;

        @JsonProperty("default")
        private Object defaultValue;

        private Boolean required;
        private String fileType;
        private Map<String, Object> schema;
        private From from;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public Object getDefaultValue()
        {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue)
        {
            this.defaultValue = defaultValue;
        }

        public Boolean getRequired()
        {
            return required;
        }

        public void setRequired(Boolean required)
        {
            this.required = required;
        }

        public String getFileType()
        {
            return fileType;
        }

        public void setFileType(String fileType)
        {
            this.fileType = fileType;
        }

        public Map<String, Object> getSchema()
        {
            return schema;
        }

        public void setSchema(Map<String, Object> schema)
        {
            this.schema = schema;
        }

        public From getFrom()
        {
            return from;
        }

        public void setFrom(From from)
        {
            this.from = from;
        }
    }

    public static class From
    {
        private String kind;
        private String nodeId;
        private String output;
        private String outputId;
        private Object value;

        public String getKind()
        {
            return kind;
        }

        public void setKind(String kind)
        {
            this.kind = kind;
        }

        public String getNodeId()
        {
            return nodeId;
        }

        public void setNodeId(String nodeId)
        {
            this.nodeId = nodeId;
        }

        public String getOutput()
        {
            return output;
        }

        public void setOutput(String output)
        {
            this.output = output;
        }

        public String getOutputId()
        {
            return outputId;
        }

        public void setOutputId(String outputId)
        {
            this.outputId = outputId;
        }

        public Object getValue()
        {
            return value;
        }

        public void setValue(Object value)
        {
            this.value = value;
        }
    }

    public static class Code
    {
        private String language;
        private String entrypoint;
        private String source;
        private String uid;
        private String appId;
        private Map<String, String> inputMapping;
        private List<String> stateWrites;
        private CodeOperation operation;

        public String getLanguage()
        {
            return language;
        }

        public void setLanguage(String language)
        {
            this.language = language;
        }

        public String getEntrypoint()
        {
            return entrypoint;
        }

        public void setEntrypoint(String entrypoint)
        {
            this.entrypoint = entrypoint;
        }

        public String getSource()
        {
            return source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getUid()
        {
            return uid;
        }

        public void setUid(String uid)
        {
            this.uid = uid;
        }

        public String getAppId()
        {
            return appId;
        }

        public void setAppId(String appId)
        {
            this.appId = appId;
        }

        public Map<String, String> getInputMapping()
        {
            return inputMapping;
        }

        public void setInputMapping(Map<String, String> inputMapping)
        {
            this.inputMapping = inputMapping;
        }

        public List<String> getStateWrites()
        {
            return stateWrites;
        }

        public void setStateWrites(List<String> stateWrites)
        {
            this.stateWrites = stateWrites;
        }

        public CodeOperation getOperation()
        {
            return operation;
        }

        public void setOperation(CodeOperation operation)
        {
            this.operation = operation;
        }
    }

    public static class CodeOperation
    {
        private String type;
        private String inputStateKey;
        private Integer splitIndex;
        private Map<String, String> outputs;
        private String semantics;

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getInputStateKey()
        {
            return inputStateKey;
        }

        public void setInputStateKey(String inputStateKey)
        {
            this.inputStateKey = inputStateKey;
        }

        public Integer getSplitIndex()
        {
            return splitIndex;
        }

        public void setSplitIndex(Integer splitIndex)
        {
            this.splitIndex = splitIndex;
        }

        public Map<String, String> getOutputs()
        {
            return outputs;
        }

        public void setOutputs(Map<String, String> outputs)
        {
            this.outputs = outputs;
        }

        public String getSemantics()
        {
            return semantics;
        }

        public void setSemantics(String semantics)
        {
            this.semantics = semantics;
        }
    }

    public static class Router
    {
        private String uid;
        private String appId;
        private List<Branch> branches;
        private String defaultTarget;

        public String getUid()
        {
            return uid;
        }

        public void setUid(String uid)
        {
            this.uid = uid;
        }

        public String getAppId()
        {
            return appId;
        }

        public void setAppId(String appId)
        {
            this.appId = appId;
        }

        public List<Branch> getBranches()
        {
            return branches;
        }

        public void setBranches(List<Branch> branches)
        {
            this.branches = branches;
        }

        public String getDefaultTarget()
        {
            return defaultTarget;
        }

        public void setDefaultTarget(String defaultTarget)
        {
            this.defaultTarget = defaultTarget;
        }
    }

    public static class Branch
    {
        private String branchId;
        private Integer level;
        private String logicalOperator;
        private List<Condition> conditions;
        private Boolean isDefault;
        private String target;
        private String targetRuntime;

        public String getBranchId()
        {
            return branchId;
        }

        public void setBranchId(String branchId)
        {
            this.branchId = branchId;
        }

        public Integer getLevel()
        {
            return level;
        }

        public void setLevel(Integer level)
        {
            this.level = level;
        }

        public String getLogicalOperator()
        {
            return logicalOperator;
        }

        public void setLogicalOperator(String logicalOperator)
        {
            this.logicalOperator = logicalOperator;
        }

        public List<Condition> getConditions()
        {
            return conditions;
        }

        public void setConditions(List<Condition> conditions)
        {
            this.conditions = conditions;
        }

        public Boolean getIsDefault()
        {
            return isDefault;
        }

        public void setIsDefault(Boolean isDefault)
        {
            this.isDefault = isDefault;
        }

        public String getTarget()
        {
            return target;
        }

        public void setTarget(String target)
        {
            this.target = target;
        }

        public String getTargetRuntime()
        {
            return targetRuntime;
        }

        public void setTargetRuntime(String targetRuntime)
        {
            this.targetRuntime = targetRuntime;
        }
    }

    public static class Condition
    {
        private String id;
        private String operator;
        private Operand left;
        private Operand right;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getOperator()
        {
            return operator;
        }

        public void setOperator(String operator)
        {
            this.operator = operator;
        }

        public Operand getLeft()
        {
            return left;
        }

        public void setLeft(Operand left)
        {
            this.left = left;
        }

        public Operand getRight()
        {
            return right;
        }

        public void setRight(Operand right)
        {
            this.right = right;
        }
    }

    public static class Operand
    {
        private String inputId;
        private String inputName;
        private String kind;
        private String nodeId;
        private String output;
        private String stateKey;
        private Object value;

        public String getInputId()
        {
            return inputId;
        }

        public void setInputId(String inputId)
        {
            this.inputId = inputId;
        }

        public String getInputName()
        {
            return inputName;
        }

        public void setInputName(String inputName)
        {
            this.inputName = inputName;
        }

        public String getKind()
        {
            return kind;
        }

        public void setKind(String kind)
        {
            this.kind = kind;
        }

        public String getNodeId()
        {
            return nodeId;
        }

        public void setNodeId(String nodeId)
        {
            this.nodeId = nodeId;
        }

        public String getOutput()
        {
            return output;
        }

        public void setOutput(String output)
        {
            this.output = output;
        }

        public String getStateKey()
        {
            return stateKey;
        }

        public void setStateKey(String stateKey)
        {
            this.stateKey = stateKey;
        }

        public Object getValue()
        {
            return value;
        }

        public void setValue(Object value)
        {
            this.value = value;
        }
    }

    public static class Llm
    {
        private String model;
        private Prompt prompt;
        private Output output;
        private Map<String, Object> provider;

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public Prompt getPrompt()
        {
            return prompt;
        }

        public void setPrompt(Prompt prompt)
        {
            this.prompt = prompt;
        }

        public Output getOutput()
        {
            return output;
        }

        public void setOutput(Output output)
        {
            this.output = output;
        }

        public Map<String, Object> getProvider()
        {
            return provider;
        }

        public void setProvider(Map<String, Object> provider)
        {
            this.provider = provider;
        }
    }

    public static class Prompt
    {
        private String system;
        private String systemTemplateFormat;
        private String userTemplate;
        private Map<String, String> inputMapping;

        public String getSystem()
        {
            return system;
        }

        public void setSystem(String system)
        {
            this.system = system;
        }

        public String getSystemTemplateFormat()
        {
            return systemTemplateFormat;
        }

        public void setSystemTemplateFormat(String systemTemplateFormat)
        {
            this.systemTemplateFormat = systemTemplateFormat;
        }

        public String getUserTemplate()
        {
            return userTemplate;
        }

        public void setUserTemplate(String userTemplate)
        {
            this.userTemplate = userTemplate;
        }

        public Map<String, String> getInputMapping()
        {
            return inputMapping;
        }

        public void setInputMapping(Map<String, String> inputMapping)
        {
            this.inputMapping = inputMapping;
        }
    }

    public static class Output
    {
        private String name;
        private String id;
        private String stateKey;
        private String endInput;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getStateKey()
        {
            return stateKey;
        }

        public void setStateKey(String stateKey)
        {
            this.stateKey = stateKey;
        }

        public String getEndInput()
        {
            return endInput;
        }

        public void setEndInput(String endInput)
        {
            this.endInput = endInput;
        }
    }

    public static class End
    {
        private String template;
        private Boolean streamOutput;
        private Integer outputMode;
        private List<Map<String, Object>> inputKeys;
        private List<String> stateKeys;

        public String getTemplate()
        {
            return template;
        }

        public void setTemplate(String template)
        {
            this.template = template;
        }

        public Boolean getStreamOutput()
        {
            return streamOutput;
        }

        public void setStreamOutput(Boolean streamOutput)
        {
            this.streamOutput = streamOutput;
        }

        public Integer getOutputMode()
        {
            return outputMode;
        }

        public void setOutputMode(Integer outputMode)
        {
            this.outputMode = outputMode;
        }

        public List<Map<String, Object>> getInputKeys()
        {
            return inputKeys;
        }

        public void setInputKeys(List<Map<String, Object>> inputKeys)
        {
            this.inputKeys = inputKeys;
        }

        public List<String> getStateKeys()
        {
            return stateKeys;
        }

        public void setStateKeys(List<String> stateKeys)
        {
            this.stateKeys = stateKeys;
        }
    }

    public static class Edge
    {
        private String id;
        private String source;
        private String target;
        private String sourceHandle;
        private String targetHandle;
        private String type;
        private String edgeType;
        private Map<String, Object> markerEnd;

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getSource()
        {
            return source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getTarget()
        {
            return target;
        }

        public void setTarget(String target)
        {
            this.target = target;
        }

        public String getSourceHandle()
        {
            return sourceHandle;
        }

        public void setSourceHandle(String sourceHandle)
        {
            this.sourceHandle = sourceHandle;
        }

        public String getTargetHandle()
        {
            return targetHandle;
        }

        public void setTargetHandle(String targetHandle)
        {
            this.targetHandle = targetHandle;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getEdgeType()
        {
            return edgeType;
        }

        public void setEdgeType(String edgeType)
        {
            this.edgeType = edgeType;
        }

        public Map<String, Object> getMarkerEnd()
        {
            return markerEnd;
        }

        public void setMarkerEnd(Map<String, Object> markerEnd)
        {
            this.markerEnd = markerEnd;
        }
    }

    public static class ConditionalEdge
    {
        private String source;
        private String sourceRuntime;
        private List<Branch> branches;
        private String defaultTarget;

        public String getSource()
        {
            return source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getSourceRuntime()
        {
            return sourceRuntime;
        }

        public void setSourceRuntime(String sourceRuntime)
        {
            this.sourceRuntime = sourceRuntime;
        }

        public List<Branch> getBranches()
        {
            return branches;
        }

        public void setBranches(List<Branch> branches)
        {
            this.branches = branches;
        }

        public String getDefaultTarget()
        {
            return defaultTarget;
        }

        public void setDefaultTarget(String defaultTarget)
        {
            this.defaultTarget = defaultTarget;
        }
    }

    public static class Execution
    {
        private String entrypoint;
        private String finishPoint;
        private List<String> inputKeys;
        private String outputTemplate;
        private List<String> outputStateKeys;
        private String outputKey;
        private Integer recursionLimit;

        public String getEntrypoint()
        {
            return entrypoint;
        }

        public void setEntrypoint(String entrypoint)
        {
            this.entrypoint = entrypoint;
        }

        public String getFinishPoint()
        {
            return finishPoint;
        }

        public void setFinishPoint(String finishPoint)
        {
            this.finishPoint = finishPoint;
        }

        public List<String> getInputKeys()
        {
            return inputKeys;
        }

        public void setInputKeys(List<String> inputKeys)
        {
            this.inputKeys = inputKeys;
        }

        public String getOutputTemplate()
        {
            return outputTemplate;
        }

        public void setOutputTemplate(String outputTemplate)
        {
            this.outputTemplate = outputTemplate;
        }

        public List<String> getOutputStateKeys()
        {
            return outputStateKeys;
        }

        public void setOutputStateKeys(List<String> outputStateKeys)
        {
            this.outputStateKeys = outputStateKeys;
        }

        public String getOutputKey()
        {
            return outputKey;
        }

        public void setOutputKey(String outputKey)
        {
            this.outputKey = outputKey;
        }

        public Integer getRecursionLimit()
        {
            return recursionLimit;
        }

        public void setRecursionLimit(Integer recursionLimit)
        {
            this.recursionLimit = recursionLimit;
        }
    }
}
