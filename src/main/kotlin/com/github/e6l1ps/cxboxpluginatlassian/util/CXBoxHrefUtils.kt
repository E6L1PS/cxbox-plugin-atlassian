package com.github.e6l1ps.cxboxpluginatlassian.util

object CXBoxHrefUtils {
    fun getWidgetTypeLink(widgetType: String): String {
        val href = when (widgetType) {
            "AdditionalInfo" -> "https://doc.cxbox.org/widget/type/additionalinfo/additionalinfo/"
            "AdditionalList" -> "https://doc.cxbox.org/widget/type/additionallist/additionallist/"
            "AssocListPopup" -> "https://doc.cxbox.org/widget/type/assoclistpopup/assoclistpopup/"
            "EmptyWidget" -> "https://doc.cxbox.org/widget/type/emptywidget/emptywidget/"
            "FilePreview" -> "https://doc.cxbox.org/widget/type/filepreview/filepreview/"
            "Form" -> "https://doc.cxbox.org/widget/type/form/form/"
            "FormPopup" -> "https://doc.cxbox.org/widget/type/formpopup/formpopup/"
            "GroupingHierarchy" -> "https://doc.cxbox.org/widget/type/groupinghierarchy/groupinghierarchy/"
            "HeaderWidget" -> "https://doc.cxbox.org/widget/type/headerwidget/headerwidget/"
            "Info" -> "https://doc.cxbox.org/widget/type/info/info/"
            "List" -> "https://doc.cxbox.org/widget/type/list/list/"
            "PickListPopup" -> "https://doc.cxbox.org/widget/type/picklistpopup/picklistpopup/"
            "StatsBlock" -> "https://doc.cxbox.org/widget/type/statblocks/statblocks/"
            else -> "https://doc.cxbox.org/widget/type/widget/"
        }
        return """<a href="$href"><code>$widgetType</code></a>"""
    }

    fun getFieldTypeLink(fieldType: String): String {
        val href = when (fieldType) {
            "checkbox" -> "https://doc.cxbox.org/widget/fields/field/checkbox/checkbox/"
            "date" -> "https://doc.cxbox.org/widget/fields/field/date/date/"
            "dateTime" -> "https://doc.cxbox.org/widget/fields/field/dateTime/dateTime/"
            "dateTimeWithSeconds" -> "https://doc.cxbox.org/widget/fields/field/dateTimeWithSeconds/dateTimeWithSeconds/"
            "dictionary" -> "https://doc.cxbox.org/widget/fields/field/dictionary/dictionary/"
            "fileUpload" -> "https://doc.cxbox.org/widget/fields/field/fileUpload/fileUpload/"
            "hidden" -> "https://doc.cxbox.org/widget/fields/field/hidden/hidden/"
            "hint" -> "https://doc.cxbox.org/widget/fields/field/hint/hint/"
            "inlinePickList" -> "https://doc.cxbox.org/widget/fields/field/inlinePickList/inlinePickList/"
            "input" -> "https://doc.cxbox.org/widget/fields/field/input/input/"
            "money" -> "https://doc.cxbox.org/widget/fields/field/money/money/"
            "multifield" -> "https://doc.cxbox.org/widget/fields/field/multifield/multifield/"
            "multivalue" -> "https://doc.cxbox.org/widget/fields/field/multivalue/multivalue/"
            "multivalueHover" -> "https://doc.cxbox.org/widget/fields/field/multivalueHover/multivalueHover/"
            "multipleSelect" -> "https://doc.cxbox.org/widget/fields/field/multipleSelect/multipleSelect/"
            "number" -> "https://doc.cxbox.org/widget/fields/field/number/number/"
            "percent" -> "https://doc.cxbox.org/widget/fields/field/percent/percent/"
            "pickList" -> "https://doc.cxbox.org/widget/fields/field/pickList/pickList/"
            "radio" -> "https://doc.cxbox.org/widget/fields/field/radio/radio/"
            "suggestionPickList" -> "https://doc.cxbox.org/widget/fields/field/suggestionpicklist/suggestionPickList/"
            "text" -> "https://doc.cxbox.org/widget/fields/field/text/text/"
            "time" -> "https://doc.cxbox.org/widget/fields/field/time/time/"
            else -> "https://doc.cxbox.org/widget/fields/fieldtypes/"
        }
        return """<a href="$href"><code>$fieldType</code></a>"""
    }
}