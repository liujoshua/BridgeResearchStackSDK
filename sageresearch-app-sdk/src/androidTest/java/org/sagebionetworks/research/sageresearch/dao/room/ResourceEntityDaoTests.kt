package org.sagebionetworks.research.sageresearch.dao.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.sageresearch.viewmodel.RoomTestHelper


@RunWith(AndroidJUnit4::class)
class ResourceEntityDaoTests: RoomTestHelper() {

    companion object {
        const val RESOURCE_ID = "testId"
        val resourceEntity = ResourceEntity(RESOURCE_ID,
                ResourceEntity.ResourceType.APP_CONFIG,
                "this should be json",
                0)
    }

    @Before
    fun setupForEachTestWithEmptyDatabase() {
        resourceDao.clear()
    }

    @Test
    fun test_upsert_and_get() {
        resourceDao.upsert(resourceEntity)
        Assert.assertEquals(resourceEntity, getValue(resourceDao.getResource(RESOURCE_ID, resourceEntity.type)))
    }

    @Test
    fun test_clear() {
        resourceDao.upsert(resourceEntity)
        resourceDao.clear()
        Assert.assertNull(getValue(resourceDao.getResource(RESOURCE_ID, resourceEntity.type)))
    }

}