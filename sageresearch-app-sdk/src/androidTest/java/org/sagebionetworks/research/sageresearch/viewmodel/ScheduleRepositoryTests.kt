/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.sageresearch.viewmodel


import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.MediumTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reactivex.Completable
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.joda.time.DateTime
import org.junit.*
import org.junit.runner.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.manager.ActivityManager
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager

import org.sagebionetworks.bridge.android.manager.SurveyManager
import org.sagebionetworks.bridge.android.manager.UploadManager
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException
import org.sagebionetworks.bridge.rest.model.Message
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepositoryHelper
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledRepositorySyncStateDao
import org.sagebionetworks.research.sageresearch.viewmodel.ReportRepositoryTests.MockReportRepository
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepositoryTests.MockScheduleRepository.Companion.participantCreatedOn
import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepositoryTests.MockScheduleRepository.Companion.syncDateFirst

//
//  Copyright © 2016-2018 Sage Bionetworks. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1.  Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2.  Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation and/or
// other materials provided with the distribution.
//
// 3.  Neither the name of the copyright holder(s) nor the names of any contributors
// may be used to endorse or promote products derived from this software without
// specific prior written permission. No license is granted to the trademarks of
// the copyright holders even if such marks are included in this software.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

@RunWith(AndroidJUnit4::class)
// ran into multi-dex issues moving this to a library project, leaving it here for now
@MediumTest
class ScheduleRepositoryTests: RoomTestHelper() {

    // No need to test any value but 14, because that is currently a bridge limitation
    val maxRequestDays = 14

    companion object {
        val activityList = "test_scheduled_activities.json"
        val testResourceMap = TestResourceHelper.testResourceMap(setOf(
                activityList))
        val syncStateDao = MockScheduleRepositorySyncStateDao()
    }

    @Before
    fun setupBeforeEachTest() {
        reportDao.clear()
        MockitoAnnotations.initMocks(this)
        scheduleRepo = MockScheduleRepository(activityDao, syncStateDao, surveyManager,
                activityManager, participantManager, authenticationManager, uploadManager, bridgeConfig)
    }

    @Test
    fun query_needsSyncedWithBridge() {
        // TODO: mdephillips 9/24/18 do tests
    }

    @Before
    @After
    public fun resetSharedPrefs() {
        syncStateDao.prefs.edit().clear().commit()
    }

    @Test
    fun syncStartDate_states() {
        assertNotNull(scheduleRepo.syncStartDate)
        assertEquals(participantCreatedOn.withTimeAtStartOfDay(), scheduleRepo.syncStartDate)
        syncStateDao.lastQueryEndDate = syncDateFirst
        assertEquals(syncDateFirst.withTimeAtStartOfDay(), scheduleRepo.syncStartDate)
    }

    @Test
    fun scheduleUpdateFailed_noInternet() {
        val activities = testResourceMap[activityList] ?: listOf()
        activityDao.clear()
        activityDao.upsert(activities)
        val schedule1 = activities.firstOrNull()
        assertNotNull(schedule1)
        assertTrue(schedule1?.needsSyncedToBridge == null || schedule1.needsSyncedToBridge == false)
        val uuid = scheduleRepo.createScheduleTaskRunUuid(schedule1?.guid ?: "")
        scheduleRepo.throwableOnUpdate = Throwable("Unable to resolve host " +
                "\"webservices.sagebase.org\", no address associated with hostname")
        scheduleRepo.updateSchedule(TaskResultBase("id", uuid)).onErrorComplete().blockingAwait()

        val newSchedule1 = activityDao.activity(schedule1?.guid ?: "")
        assertEquals(1, newSchedule1.size)
        assertTrue(newSchedule1.first().needsSyncedToBridge ?: false)
    }

    @Test
    fun scheduleUpdateFailed_accountNotFoundError() {
        val activities = testResourceMap[activityList] ?: listOf()
        activityDao.clear()
        activityDao.upsert(activities)
        val schedule1 = activities.firstOrNull()
        assertNotNull(schedule1)
        assertTrue(schedule1?.needsSyncedToBridge == null || schedule1.needsSyncedToBridge == false)
        val uuid = scheduleRepo.createScheduleTaskRunUuid(schedule1?.guid ?: "")
        scheduleRepo.throwableOnUpdate = EntityNotFoundException("Account not found.", "webservices.sagebase.org")
        scheduleRepo.updateSchedule(TaskResultBase("id", uuid)).onErrorComplete().blockingAwait()
        // See BridgeExtensions.isUnrecoverableAccountNotFoundError for logic
        // on why we don't try to re-upload account not found schedule update failures

        val newSchedule1 = activityDao.activity(schedule1?.guid ?: "")
        assertEquals(1, newSchedule1.size)
        assertFalse(newSchedule1.first().needsSyncedToBridge ?: true)
    }

    @Test
    fun scheduleUpdateFailed_clientDataTooLarge() {
        val activities = testResourceMap[activityList] ?: listOf()
        activityDao.clear()
        activityDao.upsert(activities)
        val schedule1 = activities.firstOrNull()
        assertNotNull(schedule1)
        assertNull(schedule1?.needsSyncedToBridge)
        val uuid = scheduleRepo.createScheduleTaskRunUuid(schedule1?.guid ?: "")
        scheduleRepo.throwableOnUpdate = Throwable("Client data too large, please consider a smaller payload")
        scheduleRepo.updateSchedule(TaskResultBase("id", uuid)).onErrorComplete().blockingGet()
        // See BridgeExtensions.isUnrecoverableClientDataTooLargeError for logic
        // on why we don't try to re-upload client data too large schedule update failures )

        val newSchedule1 = activityDao.activity(schedule1?.guid ?: "")
        assertEquals(1, newSchedule1.size)
        assertFalse(newSchedule1.first().needsSyncedToBridge ?: true)
    }

    @Test
    fun requestMap_LessThan14Days() {
        val start = DateTime.parse("2018-08-17T00:00:00.000-04:00")
        val end = DateTime.parse("2018-08-27T00:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(1, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-08-17T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-08-27T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
    }

    @Test
    fun requestMap_MoreThan14DaysEven() {
        val start = DateTime.parse("2018-08-18T12:00:00.000-04:00")
        val end = DateTime.parse("2018-09-14T00:12:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-09-01T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-09-14T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
        assertEquals(DateTime.parse("2018-08-18T00:00:00.000-04:00"), requestMap.keys.elementAt(1))
        assertEquals(DateTime.parse("2018-08-31T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(1)])
    }

    @Test
    fun requestMap_MoreThan14DaysRemainder() {
        val start = DateTime.parse("2018-08-17T10:00:00.000-04:00")
        val end = DateTime.parse("2018-09-09T10:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-08-27T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-09-09T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
        assertEquals(DateTime.parse("2018-08-17T00:00:00.000-04:00"), requestMap.keys.elementAt(1))
        assertEquals(DateTime.parse("2018-08-26T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(1)])
    }

    class MockScheduleRepositorySyncStateDao:
            ScheduledRepositorySyncStateDao(InstrumentationRegistry.getInstrumentation().getTargetContext()) {

        private var lastQueryEndDateLocal: DateTime? = null

        override var lastQueryEndDate: DateTime? get() {
            return lastQueryEndDateLocal
        } set(value) {
            lastQueryEndDateLocal = value
        }
    }

    lateinit var scheduleRepo: MockScheduleRepository
    @Mock
    lateinit var uploadManager: UploadManager
    @Mock
    lateinit var activityManager: ActivityManager
    @Mock
    lateinit var surveyManager: SurveyManager
    @Mock
    lateinit var participantManager: ParticipantRecordManager
    @Mock
    lateinit var authenticationManager: AuthenticationManager
    @Mock
    lateinit var bridgeConfig: BridgeConfig

    class MockScheduleRepository(scheduleDao: ScheduledActivityEntityDao,
            syncStateDao: ScheduledRepositorySyncStateDao,
            surveyManager: SurveyManager,
            val activityManager: ActivityManager,
            participantManager: ParticipantRecordManager,
            authenticationManager: AuthenticationManager,
            uploadManager: UploadManager,
            bridgeConfig: BridgeConfig
            )
        : ScheduleRepository(scheduleDao, syncStateDao,
            surveyManager, activityManager,
            participantManager, authenticationManager, uploadManager, bridgeConfig) {

        companion object {
            val participantCreatedOn = DateTime.parse("2018-08-10T10:00:00.000-04:00")
            val syncDateFirst = DateTime.parse("2018-08-24T10:00:00.000-04:00")
        }

        val dao: ScheduledActivityEntityDao = scheduleDao
        var throwableOnUpdate: Throwable? = null

        override fun now(): DateTime {
            return DateTime.now()
        }

        override fun studyStartDate(): DateTime? {
            return participantCreatedOn
        }

        override fun updateSchedulesToBridgeCompletable(schedules: List<ScheduledActivityEntity>): Completable {
            throwableOnUpdate?.let {
                `when`(activityManager.updateActivities(any()))
                        .thenReturn(rx.Single.error(it))
            } ?: run {
                `when`(activityManager.updateActivities(any()))
                        .thenReturn(rx.Single.just(Message()))
            }
            return super.updateSchedulesToBridgeCompletable(schedules)
        }
    }
}
