package org.sagebionetworks.bridge.android.manager.models

import org.sagebionetworks.bridge.rest.model.AppConfig
import org.sagebionetworks.bridge.rest.model.SurveyReference

/**
 * A ProfileDataManager is a configuration element defined in the Bridge Study Manager.  It is used
 * to describe where and how profile data should be loaded.
 */
data class ProfileDataManager(val map: Map<String, Any?>, val appConfig: AppConfig) {
    val catType:    String by map
    val type:       String by map
    val identifier: String by map
    private val items:   List<Map<String, Any?>> by map

    val profileDataItems: List<ProfileDataItem> = items.mapNotNull{ decodeProfileDataItem(it, appConfig) }
    val profileDataMap: Map<String, ProfileDataItem> = profileDataItems.associate { it.profileKey to it }

    private val _reportIds: MutableSet<String> = HashSet<String>()
    val reportIds: Set<String>
        get() {
            if (_reportIds.isEmpty()) {
                profileDataItems.forEach {
                    if (it is ReportProfileDataItem) {
                        _reportIds.add(it.demographicSchema)
                    }
                }
            }
            return _reportIds
        }

    private fun decodeProfileDataItem(itemMap: Map<String, Any?>, appConfig: AppConfig): ProfileDataItem? {
        val type = itemMap.get("type")
        val map = itemMap.withDefault { null }
        when (type) {
            "dataGroup" -> {
                return ReportProfileDataItem(map, appConfig)
            }
            "report" -> {
                return ReportProfileDataItem(map, appConfig)
            }
            "participantClientData" -> {
                return ParticipantClientDataProfileDataItem(map)
            }
            "participant" -> {
                return ParticipantProfileDataItem(map)
            }

            else -> {
                //Error unknown type
                return null
            }

        }
    }

}

interface ProfileDataItem {
    val type: String
    val itemType: String
    val profileKey: String
}

data class ReportProfileDataItem(val map: Map<String, Any?>, val appConfig: AppConfig) : ProfileDataItem {
    override val type: String by map
    override val itemType: String by map
    override val profileKey: String by map
    val dataGroups: List<String>? by map
    val readonly: Boolean by map
    val sourceKey: String by map
    val demographicSchema: String by map
    val surveyReference = appConfig.getSurveyReference(demographicSchema)
}

data class ParticipantClientDataProfileDataItem(val map: Map<String, Any?>) : ProfileDataItem {
    override val type: String by map
    override val itemType: String by map
    override val profileKey: String by map
    val fallbackKeyPath: String by map
}

data class ParticipantProfileDataItem(val map: Map<String, Any?>) : ProfileDataItem {
    override val type: String by map
    override val itemType: String by map
    override val profileKey: String by map
}

fun AppConfig.getSurveyReference(surveyIdentifier: String): SurveyReference? {
    return this.surveyReferences.firstOrNull { it.identifier == surveyIdentifier }
}