package com.blueprint.editor.data

/** The kind of UI element a dot/box marks. Mirrors <select id="sheetType"> exactly, in order. */
enum class ElementType(val label: String) {
    BUTTON("Button"),
    TEXT("Text"),
    HEADING("Heading"),
    ICON("Icon"),
    IMAGE("Image"),
    LOGO("Logo"),
    CARD("Card"),
    INPUT("Input"),
    CHECKBOX("Checkbox"),
    SWITCH("Switch"),
    NAVIGATION("Navigation"),
    TAB("Tab"),
    HEADER("Header"),
    FOOTER("Footer"),
    CONTAINER("Container"),
    DIVIDER("Divider"),
    BADGE("Badge"),
    AVATAR("Avatar"),
    LIST("List"),
    OTHER("Other");

    val wireValue: String get() = name.lowercase()

    companion object {
        val Default = BUTTON

        fun fromWireValue(value: String): ElementType =
            entries.firstOrNull { it.wireValue == value } ?: Default
    }
}
