package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ModelParametersSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 参数状态
    var parameters by remember { mutableStateOf<List<ModelParameter<*>>>(emptyList()) }

    // 参数错误状态
    val parameterErrors = remember { mutableStateMapOf<String, String>() }

    // 初始化参数
    LaunchedEffect(config.id) {
        // 构建参数列表
        val paramList = mutableListOf<ModelParameter<*>>()

        // 添加MaxTokens参数
        paramList.add(
                ModelParameter(
                        id = "max_tokens",
                        name = "最大生成Token数",
                        apiName = "max_tokens",
                        description = "控制AI每次最多生成的Token数量",
                        defaultValue = ApiPreferences.DEFAULT_MAX_TOKENS,
                        currentValue = config.maxTokens,
                        isEnabled = config.maxTokensEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 1,
                        maxValue = 16000,
                        category = ParameterCategory.GENERATION
                )
        )

        // 添加Temperature参数
        paramList.add(
                ModelParameter(
                        id = "temperature",
                        name = "温度",
                        apiName = "temperature",
                        description = "控制输出的随机性。较低的值更确定性，较高的值更随机",
                        defaultValue = ApiPreferences.DEFAULT_TEMPERATURE,
                        currentValue = config.temperature,
                        isEnabled = config.temperatureEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        // 添加TopP参数
        paramList.add(
                ModelParameter(
                        id = "top_p",
                        name = "Top-P 采样",
                        apiName = "top_p",
                        description = "作为温度的替代方案，模型仅考虑概率最高的Top-P比例的token",
                        defaultValue = ApiPreferences.DEFAULT_TOP_P,
                        currentValue = config.topP,
                        isEnabled = config.topPEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        // 添加TopK参数
        paramList.add(
                ModelParameter(
                        id = "top_k",
                        name = "Top-K 采样",
                        apiName = "top_k",
                        description = "模型仅考虑概率最高的K个token。0表示禁用",
                        defaultValue = ApiPreferences.DEFAULT_TOP_K,
                        currentValue = config.topK,
                        isEnabled = config.topKEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 0,
                        maxValue = 100,
                        category = ParameterCategory.CREATIVITY
                )
        )

        // 添加PresencePenalty参数
        paramList.add(
                ModelParameter(
                        id = "presence_penalty",
                        name = "存在惩罚",
                        apiName = "presence_penalty",
                        description = "增强模型谈论新主题的倾向。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_PRESENCE_PENALTY,
                        currentValue = config.presencePenalty,
                        isEnabled = config.presencePenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        // 添加FrequencyPenalty参数
        paramList.add(
                ModelParameter(
                        id = "frequency_penalty",
                        name = "频率惩罚",
                        apiName = "frequency_penalty",
                        description = "减少模型重复同一词语的可能性。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_FREQUENCY_PENALTY,
                        currentValue = config.frequencyPenalty,
                        isEnabled = config.frequencyPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        // 添加RepetitionPenalty参数
        paramList.add(
                ModelParameter(
                        id = "repetition_penalty",
                        name = "重复惩罚",
                        apiName = "repetition_penalty",
                        description = "进一步减少重复。1.0表示不惩罚，大于1.0会降低重复可能性",
                        defaultValue = ApiPreferences.DEFAULT_REPETITION_PENALTY,
                        currentValue = config.repetitionPenalty,
                        isEnabled = config.repetitionPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        // 更新参数列表
        parameters = paramList
    }

    // 更新参数值
    val updateParameterValue = { parameter: ModelParameter<*>, newValue: Any ->
        val newParameters =
                parameters.map { p ->
                    if (p.id == parameter.id) {
                        when (p.valueType) {
                            ParameterValueType.INT -> {
                                val intParam = p as ModelParameter<Int>
                                intParam.copy(currentValue = newValue as Int)
                            }
                            ParameterValueType.FLOAT -> {
                                val floatParam = p as ModelParameter<Float>
                                floatParam.copy(currentValue = newValue as Float)
                            }
                            ParameterValueType.STRING -> {
                                val stringParam = p as ModelParameter<String>
                                stringParam.copy(currentValue = newValue as String)
                            }
                            ParameterValueType.BOOLEAN -> {
                                val boolParam = p as ModelParameter<Boolean>
                                boolParam.copy(currentValue = newValue as Boolean)
                            }
                        }
                    } else {
                        p
                    }
                }
        parameters = newParameters
    }

    // 切换参数启用状态
    val toggleParameter = { parameter: ModelParameter<*>, isEnabled: Boolean ->
        val newParameters =
                parameters.map { p ->
                    if (p.id == parameter.id) {
                        when (p.valueType) {
                            ParameterValueType.INT -> {
                                val intParam = p as ModelParameter<Int>
                                intParam.copy(isEnabled = isEnabled)
                            }
                            ParameterValueType.FLOAT -> {
                                val floatParam = p as ModelParameter<Float>
                                floatParam.copy(isEnabled = isEnabled)
                            }
                            ParameterValueType.STRING -> {
                                val stringParam = p as ModelParameter<String>
                                stringParam.copy(isEnabled = isEnabled)
                            }
                            ParameterValueType.BOOLEAN -> {
                                val boolParam = p as ModelParameter<Boolean>
                                boolParam.copy(isEnabled = isEnabled)
                            }
                        }
                    } else {
                        p
                    }
                }
        parameters = newParameters
    }

    // 更新标准参数的辅助函数 - 作为ModelParametersSection的成员方法
    suspend fun updateStandardParameters(
            configId: String,
            parameters: List<ModelParameter<*>>,
            apiKey: String,
            apiEndpoint: String,
            modelName: String
    ) {
        // 处理标准参数
        var maxTokens = 4096
        var maxTokensEnabled = false
        var temperature = 1.0f
        var temperatureEnabled = false
        var topP = 1.0f
        var topPEnabled = false
        var topK = 0
        var topKEnabled = false
        var presencePenalty = 0.0f
        var presencePenaltyEnabled = false
        var frequencyPenalty = 0.0f
        var frequencyPenaltyEnabled = false
        var repetitionPenalty = 1.0f
        var repetitionPenaltyEnabled = false

        // 从参数列表中提取标准参数
        parameters.forEach { param ->
            when (param.id) {
                "max_tokens" -> {
                    maxTokens = (param.currentValue as Int)
                    maxTokensEnabled = param.isEnabled
                }
                "temperature" -> {
                    temperature = (param.currentValue as Float)
                    temperatureEnabled = param.isEnabled
                }
                "top_p" -> {
                    topP = (param.currentValue as Float)
                    topPEnabled = param.isEnabled
                }
                "top_k" -> {
                    topK = (param.currentValue as Int)
                    topKEnabled = param.isEnabled
                }
                "presence_penalty" -> {
                    presencePenalty = (param.currentValue as Float)
                    presencePenaltyEnabled = param.isEnabled
                }
                "frequency_penalty" -> {
                    frequencyPenalty = (param.currentValue as Float)
                    frequencyPenaltyEnabled = param.isEnabled
                }
                "repetition_penalty" -> {
                    repetitionPenalty = (param.currentValue as Float)
                    repetitionPenaltyEnabled = param.isEnabled
                }
            }
        }

        // 获取当前配置
        val config =
                configManager.getModelConfigFlow(configId).collect { collectedConfig ->
                    // 更新配置
                    val updatedConfig =
                            collectedConfig.copy(
                                    apiKey = apiKey,
                                    apiEndpoint = apiEndpoint,
                                    modelName = modelName,
                                    maxTokens = maxTokens,
                                    maxTokensEnabled = maxTokensEnabled,
                                    temperature = temperature,
                                    temperatureEnabled = temperatureEnabled,
                                    topP = topP,
                                    topPEnabled = topPEnabled,
                                    topK = topK,
                                    topKEnabled = topKEnabled,
                                    presencePenalty = presencePenalty,
                                    presencePenaltyEnabled = presencePenaltyEnabled,
                                    frequencyPenalty = frequencyPenalty,
                                    frequencyPenaltyEnabled = frequencyPenaltyEnabled,
                                    repetitionPenalty = repetitionPenalty,
                                    repetitionPenaltyEnabled = repetitionPenaltyEnabled
                            )

                    // 保存更新后的配置
                    configManager.saveModelConfig(updatedConfig)

                    // 刷新所有AI服务实例，确保使用最新配置
                    com.ai.assistance.operit.api.EnhancedAIService.refreshAllServices(
                            configManager.appContext
                    )

                    // 处理自定义参数 (如果有需要)
                    val customParams = parameters.filter { it.isCustom }
                    if (customParams.isNotEmpty()) {
                        val customParamsList =
                                customParams.map { param ->
                                    when (param.valueType) {
                                        ParameterValueType.INT -> {
                                            com.ai.assistance.operit.data.model.CustomParameterData(
                                                    id = param.id,
                                                    name = param.name,
                                                    apiName = param.apiName,
                                                    description = param.description,
                                                    defaultValue =
                                                            (param.defaultValue as Int).toString(),
                                                    currentValue =
                                                            (param.currentValue as Int).toString(),
                                                    isEnabled = param.isEnabled,
                                                    valueType = param.valueType.name,
                                                    minValue = (param.minValue as? Int)?.toString(),
                                                    maxValue = (param.maxValue as? Int)?.toString(),
                                                    category = param.category.name
                                            )
                                        }
                                        ParameterValueType.FLOAT -> {
                                            com.ai.assistance.operit.data.model.CustomParameterData(
                                                    id = param.id,
                                                    name = param.name,
                                                    apiName = param.apiName,
                                                    description = param.description,
                                                    defaultValue =
                                                            (param.defaultValue as Float)
                                                                    .toString(),
                                                    currentValue =
                                                            (param.currentValue as Float)
                                                                    .toString(),
                                                    isEnabled = param.isEnabled,
                                                    valueType = param.valueType.name,
                                                    minValue =
                                                            (param.minValue as? Float)?.toString(),
                                                    maxValue =
                                                            (param.maxValue as? Float)?.toString(),
                                                    category = param.category.name
                                            )
                                        }
                                        ParameterValueType.STRING -> {
                                            com.ai.assistance.operit.data.model.CustomParameterData(
                                                    id = param.id,
                                                    name = param.name,
                                                    apiName = param.apiName,
                                                    description = param.description,
                                                    defaultValue = param.defaultValue as String,
                                                    currentValue = param.currentValue as String,
                                                    isEnabled = param.isEnabled,
                                                    valueType = param.valueType.name,
                                                    category = param.category.name
                                            )
                                        }
                                        ParameterValueType.BOOLEAN -> {
                                            com.ai.assistance.operit.data.model.CustomParameterData(
                                                    id = param.id,
                                                    name = param.name,
                                                    apiName = param.apiName,
                                                    description = param.description,
                                                    defaultValue =
                                                            (param.defaultValue as Boolean)
                                                                    .toString(),
                                                    currentValue =
                                                            (param.currentValue as Boolean)
                                                                    .toString(),
                                                    isEnabled = param.isEnabled,
                                                    valueType = param.valueType.name,
                                                    category = param.category.name
                                            )
                                        }
                                    }
                                }

                        // 使用新方法更新自定义参数
                        configManager.updateCustomParameters(
                                configId = configId,
                                parametersJson = Json.encodeToString(customParamsList)
                        )
                    } else {
                        // 如果没有自定义参数，清空自定义参数列表
                        configManager.updateCustomParameters(
                                configId = configId,
                                parametersJson = "[]"
                        )
                    }
                }
    }

    // 保存参数设置
    val saveParameters = {
        scope.launch {
            try {
                // 使用新的方法来更新参数
                // 首先获取当前配置的API设置
                val currentConfig =
                        configManager.getModelConfigFlow(config.id).collect {
                            val apiKey = it.apiKey
                            val apiEndpoint = it.apiEndpoint
                            val modelName = it.modelName

                            // 为每个参数类型创建单独更新方法
                            updateStandardParameters(
                                    configId = config.id,
                                    parameters = parameters,
                                    apiKey = apiKey,
                                    apiEndpoint = apiEndpoint,
                                    modelName = modelName
                            )
                        }

                showNotification("参数已保存")
            } catch (e: Exception) {
                // 处理异常
                e.printStackTrace()
            }
        }
    }

    // 重置所有参数
    val resetParameters = {
        scope.launch {
            try {
                // 重置所有参数为默认值
                val resetParams =
                        parameters.map { param ->
                            when (param.valueType) {
                                ParameterValueType.INT -> {
                                    val intParam = param as ModelParameter<Int>
                                    intParam.copy(
                                            currentValue = intParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                                ParameterValueType.FLOAT -> {
                                    val floatParam = param as ModelParameter<Float>
                                    floatParam.copy(
                                            currentValue = floatParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                                ParameterValueType.STRING -> {
                                    val stringParam = param as ModelParameter<String>
                                    stringParam.copy(
                                            currentValue = stringParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                                ParameterValueType.BOOLEAN -> {
                                    val boolParam = param as ModelParameter<Boolean>
                                    boolParam.copy(
                                            currentValue = boolParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                            }
                        }
                parameters = resetParams

                // 获取当前配置的API设置
                val currentConfig =
                        configManager.getModelConfigFlow(config.id).collect {
                            val apiKey = it.apiKey
                            val apiEndpoint = it.apiEndpoint
                            val modelName = it.modelName

                            // 使用新的方法保存重置后的参数
                            updateStandardParameters(
                                    configId = config.id,
                                    parameters = resetParams,
                                    apiKey = apiKey,
                                    apiEndpoint = apiEndpoint,
                                    modelName = modelName
                            )
                        }

                showNotification("所有参数已重置为默认值")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 分类参数
    val generationParams = parameters.filter { it.category == ParameterCategory.GENERATION }
    val creativityParams = parameters.filter { it.category == ParameterCategory.CREATIVITY }
    val repetitionParams = parameters.filter { it.category == ParameterCategory.REPETITION }

    // UI部分
    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "模型参数设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
            }

            // 参数描述
            Text(
                    text = "启用参数时，该参数将被包含在API请求中。默认情况下，所有参数均处于关闭状态，使用模型的默认值。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
            )

            // 生成参数部分
            if (generationParams.isNotEmpty()) {
                SectionTitle(title = "生成参数", icon = Icons.Default.AutoFixHigh)
                generationParams.forEach { parameter ->
                    ParameterItem(
                            parameter = parameter,
                            onValueChange = { newValue ->
                                updateParameterValue(parameter, newValue)
                            },
                            onToggle = { isEnabled -> toggleParameter(parameter, isEnabled) },
                            error = parameterErrors[parameter.id],
                            onErrorChange = { error ->
                                if (error != null) {
                                    parameterErrors[parameter.id] = error
                                } else {
                                    parameterErrors.remove(parameter.id)
                                }
                            }
                    )

                    // 温度推荐显示
                    if (parameter.apiName == "temperature") {
                        TemperatureRecommendationRow()
                    }
                }
            }

            // 创造性参数部分
            if (creativityParams.isNotEmpty()) {
                SectionTitle(title = "创造性参数", icon = Icons.Default.Lightbulb)
                creativityParams.forEach { parameter ->
                    ParameterItem(
                            parameter = parameter,
                            onValueChange = { newValue ->
                                updateParameterValue(parameter, newValue)
                            },
                            onToggle = { isEnabled -> toggleParameter(parameter, isEnabled) },
                            error = parameterErrors[parameter.id],
                            onErrorChange = { error ->
                                if (error != null) {
                                    parameterErrors[parameter.id] = error
                                } else {
                                    parameterErrors.remove(parameter.id)
                                }
                            }
                    )
                }
            }

            // 重复控制参数部分
            if (repetitionParams.isNotEmpty()) {
                SectionTitle(title = "重复控制参数", icon = Icons.Default.Repeat)
                repetitionParams.forEach { parameter ->
                    ParameterItem(
                            parameter = parameter,
                            onValueChange = { newValue ->
                                updateParameterValue(parameter, newValue)
                            },
                            onToggle = { isEnabled -> toggleParameter(parameter, isEnabled) },
                            error = parameterErrors[parameter.id],
                            onErrorChange = { error ->
                                if (error != null) {
                                    parameterErrors[parameter.id] = error
                                } else {
                                    parameterErrors.remove(parameter.id)
                                }
                            }
                    )
                }
            }

            // 操作按钮
            Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                        onClick = { resetParameters() },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重置",
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重置参数")
                }

                Button(
                        onClick = { saveParameters() },
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                ),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存",
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存参数")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )
    }
    Divider(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ParameterItem(
        parameter: ModelParameter<*>,
        onValueChange: (Any) -> Unit,
        onToggle: (Boolean) -> Unit,
        error: String? = null,
        onErrorChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = parameter.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                )

                if (parameter.description.isNotEmpty()) {
                    Text(
                            text = parameter.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                        text = "API名称: ${parameter.apiName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 启用/禁用开关
                Switch(
                        checked = parameter.isEnabled,
                        onCheckedChange = { onToggle(it) },
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor =
                                                MaterialTheme.colorScheme.primaryContainer
                                )
                )

                // 展开/收起按钮
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                            imageVector =
                                    if (expanded) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 参数值设置（仅在展开时显示）
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                when (parameter.valueType) {
                    ParameterValueType.INT -> {
                        val intParam = parameter as ModelParameter<Int>
                        var textValue by remember {
                            mutableStateOf(intParam.currentValue.toString())
                        }

                        OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    textValue = it
                                    try {
                                        val intValue = it.toInt()
                                        onErrorChange(null)
                                        onValueChange(intValue)
                                    } catch (e: NumberFormatException) {
                                        onErrorChange("必须是整数")
                                    }
                                },
                                label = { Text("值") },
                                isError = error != null,
                                supportingText = {
                                    if (error != null) {
                                        Text(error)
                                    } else if (intParam.minValue != null &&
                                                    intParam.maxValue != null
                                    ) {
                                        Text("范围: ${intParam.minValue} - ${intParam.maxValue}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ParameterValueType.FLOAT -> {
                        val floatParam = parameter as ModelParameter<Float>
                        var textValue by remember {
                            mutableStateOf(floatParam.currentValue.toString())
                        }

                        OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    textValue = it
                                    try {
                                        val floatValue = it.toFloat()
                                        onErrorChange(null)
                                        onValueChange(floatValue)
                                    } catch (e: NumberFormatException) {
                                        onErrorChange("必须是浮点数")
                                    }
                                },
                                label = { Text("值") },
                                isError = error != null,
                                supportingText = {
                                    if (error != null) {
                                        Text(error)
                                    } else if (floatParam.minValue != null &&
                                                    floatParam.maxValue != null
                                    ) {
                                        Text("范围: ${floatParam.minValue} - ${floatParam.maxValue}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ParameterValueType.STRING -> {
                        val stringParam = parameter as ModelParameter<String>

                        OutlinedTextField(
                                value = stringParam.currentValue,
                                onValueChange = { onValueChange(it) },
                                label = { Text("值") },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ParameterValueType.BOOLEAN -> {
                        val boolParam = parameter as ModelParameter<Boolean>

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "值:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 16.dp)
                            )
                            Switch(
                                    checked = boolParam.currentValue,
                                    onCheckedChange = { onValueChange(it) }
                            )
                        }
                    }
                }

                // 显示默认值
                Text(
                        text = "默认值: ${parameter.defaultValue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
    }
}

@Composable
private fun TemperatureRecommendationRow() {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = "温度推荐设置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )

        Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
            Text(
                    text = "1.3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
