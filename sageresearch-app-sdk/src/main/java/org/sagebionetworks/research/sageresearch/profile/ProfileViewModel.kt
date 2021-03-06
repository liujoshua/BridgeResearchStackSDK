package org.sagebionetworks.research.sageresearch.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import hu.akarnokd.rxjava.interop.RxJavaInterop.toV2Observable
import io.reactivex.Single
import org.sagebionetworks.bridge.android.manager.models.ProfileDataSource
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.bridge.rest.model.SharingScope
import org.sagebionetworks.bridge.rest.model.StudyParticipant
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.SurveyRepository
import org.sagebionetworks.research.sageresearch.repos.BridgeRepositoryManager
import org.slf4j.LoggerFactory


open class ProfileViewModel(val bridgeRepoManager: BridgeRepositoryManager, val reportRepo: ReportRepository, val appConfigRepo: AppConfigRepository, val surveyRepo: SurveyRepository) : ViewModel() {

    private val LOGGER = LoggerFactory.getLogger(ProfileViewModel::class.java)

    val profileManager = ProfileManager(reportRepo, appConfigRepo)

    val compositeDisposable = io.reactivex.disposables.CompositeDisposable()

    var currentScheduledActivity: ScheduledActivityEntity? = null

    private var cachedProfileDataLoader: ProfileDataLoader? = null

    private fun profileDataLoader(): LiveData<ProfileDataLoader> {
        return profileManager.profileDataLoader()
    }

    private fun profileDataSource(): LiveData<Map<String, ProfileDataSource>> {
        return profileManager.loadProfileDataSources()
    }

    fun profileData(key: String): LiveData<Pair<ProfileDataSource?, ProfileDataLoader?>> {

        val mediator = MediatorLiveData<Pair<ProfileDataSource?, ProfileDataLoader?>>().apply {
            var profileDataLoader: ProfileDataLoader? = null
            var profileDataSource: ProfileDataSource? = null

            fun update() {
                if (profileDataLoader != null && profileDataSource != null) {
                    this.value = Pair(profileDataSource, profileDataLoader)
                }
            }

            addSource(profileDataSource()) {
                if (it != null) {
                    profileDataSource = it.get(key)
                }
                update()
            }

            addSource(profileDataLoader()) {
                profileDataLoader = it
                cachedProfileDataLoader = it
                update()
            }
        }
        return mediator

    }

    fun saveStudyParticipantValue(value: String, profileItemKey: String) {
        var studyParticipant: StudyParticipant? = null
        when (profileItemKey) {
            "firstName" -> {
                cachedProfileDataLoader?.participantData?.firstName = value
                studyParticipant = StudyParticipant()
                studyParticipant.firstName = value

            }
            "sharingScope" -> {
                val scope = SharingScope.fromValue(value)
                cachedProfileDataLoader?.participantData?.sharingScope = scope
                studyParticipant = StudyParticipant()
                studyParticipant.sharingScope = scope
            }
        }
        if (studyParticipant != null) {
            compositeDisposable.add(toV2Observable(BridgeDataProvider.getInstance()
                    .updateStudyParticipant(studyParticipant))
                    .subscribe(
                            { userSessionInfo -> LOGGER.info("Successfully updated study participant") },
                            { throwable -> LOGGER.warn("Error updating study participant") }))
        }
    }

    fun loadSurvey(surveyReference: SurveyReference): Single<Survey> {
        return surveyRepo.getSurvey(surveyReference).doOnSuccess { loadScheduledActivity(it.identifier) }
    }

    private fun loadScheduledActivity(surveyId: String) {
        compositeDisposable.add(bridgeRepoManager.scheduleRepo.scheduleDao.activityGroupFlowable(setOf(surveyId))
                .firstOrError()
                .subscribe({ currentScheduledActivity = it.firstOrNull() }, { "fail" })
        )
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}
