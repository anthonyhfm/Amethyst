package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

object GemBuiltInNodes {
    private val hostCategory = GemNodeCategory(id = "host", label = "Host")
    private val ledCategory = GemNodeCategory(id = "led", label = "LED")
    private val valueCategory = GemNodeCategory(id = "value", label = "Value")
    private val mathCategory = GemNodeCategory(id = "math", label = "Math")
    private val logicCategory = GemNodeCategory(id = "logic", label = "Logic")
    private val structureCategory = GemNodeCategory(id = "structure", label = "Structure")
    private val timingCategory = GemNodeCategory(id = "timing", label = "Timing")

    object TypeIds {
        const val HOST_LED_INPUT = "amethyst.host.led.in"
        const val HOST_MIDI_INPUT = "amethyst.host.midi.in"
        const val HOST_LED_OUTPUT = "amethyst.host.led.out"
        const val HOST_MIDI_OUTPUT = "amethyst.host.midi.out"
        const val CONSTANT_NUMBER = "amethyst.constant.number"
        const val CONSTANT_BOOLEAN = "amethyst.constant.boolean"
        const val LED_UNPACK = "amethyst.led.unpack"
        const val LED_PACK = "amethyst.led.pack"
        const val NUMBER_ADD = "amethyst.math.number.add"
        const val NUMBER_SUBTRACT = "amethyst.math.number.subtract"
        const val NUMBER_MULTIPLY = "amethyst.math.number.multiply"
        const val NUMBER_DIVIDE = "amethyst.math.number.divide"
        const val NUMBER_CLAMP = "amethyst.math.number.clamp"
        const val NUMBER_SQRT = "amethyst.math.number.sqrt"
        const val NUMBER_ABS = "amethyst.math.number.abs"
        const val NUMBER_FLOOR = "amethyst.math.number.floor"
        const val NUMBER_CEIL = "amethyst.math.number.ceil"
        const val NUMBER_ROUND = "amethyst.math.number.round"
        const val NUMBER_MIN = "amethyst.math.number.min"
        const val NUMBER_MAX = "amethyst.math.number.max"
        const val SUBGRAPH_CALL = "amethyst.structure.subgraph.call"
        const val LOGIC_AND = "amethyst.logic.boolean.and"
        const val LOGIC_OR = "amethyst.logic.boolean.or"
        const val LOGIC_NOT = "amethyst.logic.boolean.not"
        const val LOGIC_XOR = "amethyst.logic.boolean.xor"
        const val LOGIC_NUMBER_COMPARE = "amethyst.logic.number.compare"
        const val LOGIC_BRANCH = "amethyst.logic.signal.branch"
        const val LOGIC_GATE = "amethyst.logic.signal.gate"
        const val CONSTANT_COLOR = "amethyst.constant.color"
        const val DELAY = "amethyst.timing.delay"
    }

    const val HOST_PORT_ID_STATE_KEY: String = "hostPortId"

    val hostLedInput: GemNodeDescriptor = hostSignalInput(
        typeId = TypeIds.HOST_LED_INPUT,
        label = "LED Input",
        description = "Root host LED signal entry point for the gem graph.",
        domain = GemSignalDomain.LED
    ).copy(defaultState = hostPortState("led-in"))

    val hostLedOutput: GemNodeDescriptor = hostSignalOutput(
        typeId = TypeIds.HOST_LED_OUTPUT,
        label = "LED Output",
        description = "Routes the graph LED signal back to the host contract.",
        domain = GemSignalDomain.LED
    ).copy(defaultState = hostPortState("led-out"))

    val hostMidiInput: GemNodeDescriptor = hostSignalInput(
        typeId = TypeIds.HOST_MIDI_INPUT,
        label = "MIDI Input",
        description = "Root host MIDI signal entry point for the gem graph.",
        domain = GemSignalDomain.MIDI
    ).copy(defaultState = hostPortState("midi-in"))

    val hostMidiOutput: GemNodeDescriptor = hostSignalOutput(
        typeId = TypeIds.HOST_MIDI_OUTPUT,
        label = "MIDI Output",
        description = "Routes the graph MIDI signal back to the host contract.",
        domain = GemSignalDomain.MIDI
    ).copy(defaultState = hostPortState("midi-out"))

    val constantNumber: GemNodeDescriptor = constantNode(
        typeId = TypeIds.CONSTANT_NUMBER,
        label = "Number Constant",
        description = "Emits a stored number value.",
        valueType = GemValueType.Number,
        defaultValue = GemValue.Number(0.0)
    )

    val constantBoolean: GemNodeDescriptor = constantNode(
        typeId = TypeIds.CONSTANT_BOOLEAN,
        label = "Boolean Constant",
        description = "Emits a stored boolean value.",
        valueType = GemValueType.Boolean,
        defaultValue = GemValue.Boolean(false)
    )

    val ledUnpack: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LED_UNPACK,
        label = "LED Unpack",
        category = ledCategory,
        description = "Unpacks a single LED signal into its individual number fields.",
        inputs = listOf(
            inputPin(
                id = "signal",
                label = "Signal",
                type = GemPinType.Signal(GemSignalDomain.LED),
                required = true
            )
        ),
        outputs = listOf(
            outputPin(id = "r",     label = "R",     type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "g",     label = "G",     type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "b",     label = "B",     type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "x",     label = "X",     type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "y",     label = "Y",     type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "layer", label = "Layer", type = GemPinType.Value(GemValueType.Number))
        )
    )

    val ledPack: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LED_PACK,
        label = "LED Pack",
        category = ledCategory,
        description = "Creates a new LED signal from individual number fields. The signal originates from the gem device.",
        inputs = listOf(
            inputPin(id = "r",     label = "R",     type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(1.0)),
            inputPin(id = "g",     label = "G",     type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(1.0)),
            inputPin(id = "b",     label = "B",     type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(1.0)),
            inputPin(id = "x",     label = "X",     type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "y",     label = "Y",     type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "layer", label = "Layer", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0))
        ),
        outputs = listOf(
            outputPin(id = "signal", label = "Signal", type = GemPinType.Signal(GemSignalDomain.LED))
        )
    )

    val numberAdd: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_ADD,
        label = "Add",
        description = "Adds two numbers.",
        defaultA = 0.0, defaultB = 0.0
    )

    val numberSubtract: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_SUBTRACT,
        label = "Subtract",
        description = "Subtracts B from A.",
        defaultA = 0.0, defaultB = 0.0
    )

    val numberMultiply: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_MULTIPLY,
        label = "Multiply",
        description = "Multiplies two numbers.",
        defaultA = 1.0, defaultB = 1.0
    )

    val numberDivide: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_DIVIDE,
        label = "Divide",
        description = "Divides A by B. Returns 0 when B is 0.",
        defaultA = 0.0, defaultB = 1.0
    )

    val numberClamp: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.NUMBER_CLAMP,
        label = "Clamp",
        category = mathCategory,
        description = "Clamps a number between min and max.",
        inputs = listOf(
            inputPin(id = "value", label = "Value", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "min",   label = "Min",   type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "max",   label = "Max",   type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(1.0))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Number))
        )
    )

    val numberSqrt: GemNodeDescriptor = unaryNumberNode(
        typeId = TypeIds.NUMBER_SQRT,
        label = "Sqrt",
        description = "Square root of value.",
        inputId = "value", inputLabel = "Value"
    )

    val numberAbs: GemNodeDescriptor = unaryNumberNode(
        typeId = TypeIds.NUMBER_ABS,
        label = "Abs",
        description = "Absolute value.",
        inputId = "value", inputLabel = "Value"
    )

    val numberFloor: GemNodeDescriptor = unaryNumberNode(
        typeId = TypeIds.NUMBER_FLOOR,
        label = "Floor",
        description = "Rounds down to the nearest integer.",
        inputId = "value", inputLabel = "Value"
    )

    val numberCeil: GemNodeDescriptor = unaryNumberNode(
        typeId = TypeIds.NUMBER_CEIL,
        label = "Ceil",
        description = "Rounds up to the nearest integer.",
        inputId = "value", inputLabel = "Value"
    )

    val numberRound: GemNodeDescriptor = unaryNumberNode(
        typeId = TypeIds.NUMBER_ROUND,
        label = "Round",
        description = "Rounds to the nearest integer.",
        inputId = "value", inputLabel = "Value"
    )

    val numberMin: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_MIN,
        label = "Min",
        description = "Returns the smaller of two numbers.",
        defaultA = 0.0, defaultB = 0.0
    )

    val numberMax: GemNodeDescriptor = binaryNumberNode(
        typeId = TypeIds.NUMBER_MAX,
        label = "Max",
        description = "Returns the larger of two numbers.",
        defaultA = 0.0, defaultB = 0.0
    )

    val logicAnd: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_AND,
        label = "AND",
        category = logicCategory,
        description = "Logical AND of two booleans.",
        inputs = listOf(
            inputPin(id = "a", label = "A", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false)),
            inputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Boolean))
        )
    )

    val logicOr: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_OR,
        label = "OR",
        category = logicCategory,
        description = "Logical OR of two booleans.",
        inputs = listOf(
            inputPin(id = "a", label = "A", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false)),
            inputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Boolean))
        )
    )

    val logicNot: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_NOT,
        label = "NOT",
        category = logicCategory,
        description = "Logical NOT (inverts a boolean).",
        inputs = listOf(
            inputPin(id = "value", label = "Value", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Boolean))
        )
    )

    val logicXor: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_XOR,
        label = "XOR",
        category = logicCategory,
        description = "Logical XOR of two booleans.",
        inputs = listOf(
            inputPin(id = "a", label = "A", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false)),
            inputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Boolean))
        )
    )

    private val compareOpDefinition = GemEnumDefinition(
        id = "compare-op",
        label = "Operator",
        options = listOf(
            GemEnumOption(id = "eq", label = "="),
            GemEnumOption(id = "neq", label = "≠"),
            GemEnumOption(id = "gt", label = ">"),
            GemEnumOption(id = "gte", label = "≥"),
            GemEnumOption(id = "lt", label = "<"),
            GemEnumOption(id = "lte", label = "≤"),
        )
    )

    val logicNumberCompare: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_NUMBER_COMPARE,
        label = "Compare",
        category = logicCategory,
        description = "Compares two numbers and outputs a boolean.",
        inputs = listOf(
            inputPin(id = "a", label = "A", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0)),
            inputPin(id = "op", label = "Op", type = GemPinType.Value(GemValueType.Enum(compareOpDefinition)), required = true, defaultValue = GemValue.Enum(enumId = "compare-op", optionId = "eq"))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Boolean))
        )
    )

    val logicBranch: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_BRANCH,
        label = "Branch",
        category = logicCategory,
        description = "Routes a signal to the 'True' or 'False' output depending on the condition.",
        inputs = listOf(
            inputPin(id = "condition", label = "Condition", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(false)),
            inputPin(id = "signal",    label = "Signal",    type = GemPinType.Signal(GemSignalDomain.LED), required = true)
        ),
        outputs = listOf(
            outputPin(id = "true",  label = "True",  type = GemPinType.Signal(GemSignalDomain.LED)),
            outputPin(id = "false", label = "False", type = GemPinType.Signal(GemSignalDomain.LED))
        )
    )

    val logicGate: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.LOGIC_GATE,
        label = "Gate",
        category = logicCategory,
        description = "Passes the signal through only when enabled is true; blocks it otherwise.",
        inputs = listOf(
            inputPin(id = "enabled", label = "Enabled", type = GemPinType.Value(GemValueType.Boolean), required = true, defaultValue = GemValue.Boolean(true)),
            inputPin(id = "signal",  label = "Signal",  type = GemPinType.Signal(GemSignalDomain.LED), required = true)
        ),
        outputs = listOf(
            outputPin(id = "out", label = "Out", type = GemPinType.Signal(GemSignalDomain.LED))
        )
    )

    val colorConstant: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.CONSTANT_COLOR,
        label = "Color Constant",
        category = valueCategory,
        description = "Emits a stored color as separate R, G, B number channels.",
        outputs = listOf(
            outputPin(id = "r", label = "R", type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "g", label = "G", type = GemPinType.Value(GemValueType.Number)),
            outputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Number))
        ),
        state = listOf(
            stateField(
                id = "value",
                label = "Color",
                type = GemValueType.Color,
                required = true,
                defaultValue = GemValue.Color(GemColor(red = 1f, green = 0f, blue = 0f))
            )
        )
    )

    val delay: GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.DELAY,
        label = "Delay",
        category = timingCategory,
        description = "Forwards the incoming signal after a given delay in milliseconds.",
        inputs = listOf(
            inputPin(id = "signal_in", label = "Signal", type = GemPinType.AnySignal, required = true),
            inputPin(id = "delay",     label = "Delay (ms)", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0))
        ),
        outputs = listOf(
            outputPin(id = "signal_out", label = "Signal", type = GemPinType.AnySignal)
        )
    )

    val all: List<GemNodeDescriptor> = listOf(
        hostLedInput,
        hostLedOutput,
        hostMidiInput,
        hostMidiOutput,
        constantNumber,
        constantBoolean,
        ledUnpack,
        ledPack,
        numberAdd,
        numberSubtract,
        numberMultiply,
        numberDivide,
        numberClamp,
        numberSqrt,
        numberAbs,
        numberFloor,
        numberCeil,
        numberRound,
        numberMin,
        numberMax,
        logicAnd,
        logicOr,
        logicNot,
        logicXor,
        logicNumberCompare,
        logicBranch,
        logicGate,
        colorConstant,
        delay,
    )

    fun subgraphCallDescriptor(subgraph: GemGraph): GemNodeDescriptor = gemNodeDescriptor(
        typeId = TypeIds.SUBGRAPH_CALL,
        label = subgraph.label.ifBlank { "Subgraph Call" },
        category = structureCategory,
        description = "Invokes subgraph '${subgraph.id}' through its explicit interface.",
        inputs = subgraph.subgraphInterface.inputs.map { port ->
            inputPin(
                id = port.id,
                label = port.label,
                type = port.type,
                required = port.required,
                defaultValue = port.defaultValue,
                groupId = port.groupId,
                sortOrder = port.sortOrder
            )
        },
        outputs = subgraph.subgraphInterface.outputs.map { port ->
            outputPin(
                id = port.id,
                label = port.label,
                type = port.type,
                groupId = port.groupId,
                sortOrder = port.sortOrder
            )
        }
    )

    fun instantiateSubgraphCall(
        nodeId: String,
        subgraph: GemGraph,
        label: String = subgraph.label.ifBlank { "Subgraph Call" }
    ): GemNodeInstance = subgraphCallDescriptor(subgraph)
        .instantiate(nodeId = nodeId, label = label)
        .copy(subgraphId = subgraph.id)

    private fun hostSignalInput(
        typeId: String,
        label: String,
        description: String,
        domain: GemSignalDomain
    ): GemNodeDescriptor = gemNodeDescriptor(
        typeId = typeId,
        label = label,
        category = hostCategory,
        description = description,
        outputs = listOf(
            outputPin(
                id = "signal",
                label = "Signal",
                type = GemPinType.Signal(domain)
            )
        )
    )

    private fun hostSignalOutput(
        typeId: String,
        label: String,
        description: String,
        domain: GemSignalDomain
    ): GemNodeDescriptor = gemNodeDescriptor(
        typeId = typeId,
        label = label,
        category = hostCategory,
        description = description,
        inputs = listOf(
            inputPin(
                id = "signal",
                label = "Signal",
                type = GemPinType.Signal(domain),
                required = true
            )
        )
    )

    private fun constantNode(
        typeId: String,
        label: String,
        description: String,
        valueType: GemValueType,
        defaultValue: GemValue
    ): GemNodeDescriptor = gemNodeDescriptor(
        typeId = typeId,
        label = label,
        category = valueCategory,
        description = description,
        outputs = listOf(
            outputPin(
                id = "value",
                label = "Value",
                type = GemPinType.Value(valueType)
            )
        ),
        state = listOf(
            stateField(
                id = "value",
                label = "Value",
                type = valueType,
                required = true,
                defaultValue = defaultValue
            )
        )
    )

    fun hostPortState(portId: String): Map<String, JsonElement> = mapOf(
        HOST_PORT_ID_STATE_KEY to JsonPrimitive(portId)
    )

    fun constantNumberState(value: Double): Map<String, JsonElement> = mapOf(
        "value" to GemJsonPersistence.json.encodeToJsonElement(GemValue.serializer(), GemValue.Number(value))
    )

    private fun binaryNumberNode(
        typeId: String,
        label: String,
        description: String,
        defaultA: Double,
        defaultB: Double
    ): GemNodeDescriptor = gemNodeDescriptor(
        typeId = typeId,
        label = label,
        category = mathCategory,
        description = description,
        inputs = listOf(
            inputPin(id = "a", label = "A", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(defaultA)),
            inputPin(id = "b", label = "B", type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(defaultB))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Number))
        )
    )

    private fun unaryNumberNode(
        typeId: String,
        label: String,
        description: String,
        inputId: String,
        inputLabel: String
    ): GemNodeDescriptor = gemNodeDescriptor(
        typeId = typeId,
        label = label,
        category = mathCategory,
        description = description,
        inputs = listOf(
            inputPin(id = inputId, label = inputLabel, type = GemPinType.Value(GemValueType.Number), required = true, defaultValue = GemValue.Number(0.0))
        ),
        outputs = listOf(
            outputPin(id = "result", label = "Result", type = GemPinType.Value(GemValueType.Number))
        )
    )
}
