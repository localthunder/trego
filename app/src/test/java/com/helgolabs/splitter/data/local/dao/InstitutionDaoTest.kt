package com.helgolabs.trego.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.helgolabs.trego.TestApplication
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.local.entities.InstitutionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class InstitutionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var institutionDao: InstitutionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        institutionDao = database.institutionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetInstitution() = runBlocking {
        // Create an institution entity
        val institution = InstitutionEntity(
            id = "inst001",
            name = "Test Institution",
            bic = "TESTBIC",
            transactionTotalDays = "30",
            countries = listOf("US", "GB"),
            logo = "logo_url",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )

        // Insert the institution
        institutionDao.insert(institution)

        // Retrieve and verify the institution
        val retrievedInstitution = institutionDao.getInstitutionById("inst001")
        assertNotNull(retrievedInstitution)
        assertEquals("inst001", retrievedInstitution?.id)
        assertEquals("Test Institution", retrievedInstitution?.name)
        assertEquals("TESTBIC", retrievedInstitution?.bic)
        assertEquals("30", retrievedInstitution?.transactionTotalDays)
        assertEquals(listOf("US", "GB"), retrievedInstitution?.countries)
        assertEquals("logo_url", retrievedInstitution?.logo)
        assertEquals("2024-08-01", retrievedInstitution?.createdAt)
        assertEquals("2024-08-01", retrievedInstitution?.updatedAt)
    }

    @Test
    fun getAllInstitutions() = runBlocking {
        // Insert institutions
        val institution1 = InstitutionEntity(
            id = "inst001",
            name = "Institution One",
            bic = "BIC001",
            transactionTotalDays = "15",
            countries = listOf("US"),
            logo = "logo1_url",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )
        val institution2 = InstitutionEntity(
            id = "inst002",
            name = "Institution Two",
            bic = "BIC002",
            transactionTotalDays = "30",
            countries = listOf("GB"),
            logo = "logo2_url",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )
        institutionDao.insert(institution1)
        institutionDao.insert(institution2)

        // Retrieve and verify all institutions
        val institutions = institutionDao.getAllInstitutions()
        assertEquals(2, institutions.size)
        assertTrue(institutions.any { it.id == "inst001" })
        assertTrue(institutions.any { it.id == "inst002" })
    }
}
