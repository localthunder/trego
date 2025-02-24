package com.helgolabs.trego.data.local.repositories

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.GroupMemberDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.data.sync.managers.PaymentSyncManager
import com.helgolabs.trego.utils.AppCoroutineDispatchers
import com.helgolabs.trego.utils.NetworkUtils
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.sql.Timestamp

@ExperimentalCoroutinesApi
class PaymentRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var paymentDao: PaymentDao
    private lateinit var paymentSplitDao: PaymentSplitDao
    private lateinit var groupDao: GroupDao
    private lateinit var groupMemberDao: GroupMemberDao
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var apiService: ApiService
    private lateinit var context: Context
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var paymentSyncManager: PaymentSyncManager

    private val testDispatchers = AppCoroutineDispatchers()

    val timestampNow = Timestamp(System.currentTimeMillis()).toString()


    @Before
    fun setup() {
        paymentDao = mockk()
        paymentSplitDao = mockk()
        groupDao = mockk()
        groupMemberDao = mockk()
        syncMetadataDao = mockk()
        apiService = mockk()
        context = mockk()
        paymentSyncManager = mockk()

        mockkObject(NetworkUtils)

        paymentRepository = PaymentRepository(
            paymentDao,
            paymentSplitDao,
            groupDao,
            groupMemberDao,
            apiService,
            testDispatchers,
            context,
            syncMetadataDao,
            paymentSyncManager
        )
    }

    @Test
    fun `getPaymentById returns flow of specific payment`() = runTest {
        val paymentId = 1
        val mockPayment = PaymentEntity(
        id = 1,
        groupId = 1,
        paidByUserId = 1,
        transactionId = "txn001",
        amount = 100.0,
        description = "Test payment",
        notes = "Notes",
        paymentDate = "2024-08-24",
        createdBy = 1,
        updatedBy = 1,
        createdAt = "2024-08-24",
        updatedAt = "2024-08-24",
        splitMode = "even",
        institutionName = "Test Institution",
        paymentType = "credit",
        currency = "USD",
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_SYNC
    )

        coEvery { paymentDao.getPaymentById(paymentId) } returns flowOf(mockPayment)

        val result = paymentRepository.getPaymentById(paymentId)

        result.collect { payment ->
            assertEquals(mockPayment.toModel(), payment)
        }

        coVerify { paymentDao.getPaymentById(paymentId) }
    }

    @Test
    fun `getPaymentByTransactionId returns flow of specific payment`() = runTest {
        val transactionId = "transaction123"
        val mockPayment = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        coEvery { paymentDao.getPaymentByTransactionId(transactionId) } returns flowOf(mockPayment)

        val result = paymentRepository.getPaymentByTransactionId(transactionId)

        result.collect { payment ->
            assertEquals(mockPayment.toModel(), payment)
        }

        coVerify { paymentDao.getPaymentByTransactionId(transactionId) }
    }

    @Test
    fun `getPaymentsByGroup returns flow of payments for a group`() = runTest {
        val groupId = 1
        val mockPayments = listOf(
            PaymentEntity(
                id = 1,
                groupId = 1,
                paidByUserId = 1,
                transactionId = "txn001",
                amount = 100.0,
                description = "Test payment",
                notes = "Notes",
                paymentDate = "2024-08-24",
                createdBy = 1,
                updatedBy = 1,
                createdAt = "2024-08-24",
                updatedAt = "2024-08-24",
                splitMode = "even",
                institutionName = "Test Institution",
                paymentType = "credit",
                currency = "USD",
                deletedAt = null,
                syncStatus = SyncStatus.PENDING_SYNC
            ),
            PaymentEntity(
                id = 2,
                groupId = 1,
                paidByUserId = 1,
                transactionId = "txn001",
                amount = 100.0,
                description = "Test payment",
                notes = "Notes",
                paymentDate = "2024-08-24",
                createdBy = 1,
                updatedBy = 1,
                createdAt = "2024-08-24",
                updatedAt = "2024-08-24",
                splitMode = "even",
                institutionName = "Test Institution",
                paymentType = "credit",
                currency = "USD",
                deletedAt = null,
                syncStatus = SyncStatus.PENDING_SYNC
            )
        )

        coEvery { paymentDao.getPaymentsByGroup(groupId) } returns flowOf(mockPayments)
        every { NetworkUtils.isOnline() } returns false

        val result = paymentRepository.getPaymentsByGroup(groupId)

        result.collect { payments ->
            assertEquals(mockPayments, payments)
        }

        coVerify { paymentDao.getPaymentsByGroup(groupId) }
    }

    @Test
    fun `createPayment creates payment successfully`() = runTest {
        val payment = Payment(
                id = 1,
                groupId = 1,
                paidByUserId = 1,
                transactionId = "txn001",
                amount = 100.0,
                description = "Test payment",
                notes = "Notes",
                paymentDate = "2024-08-24",
                createdBy = 1,
                updatedBy = 1,
                createdAt = "2024-08-24",
                updatedAt = "2024-08-24",
                splitMode = "even",
                institutionName = "Test Institution",
                paymentType = "credit",
                currency = "USD",
                deletedAt = null,
            )
        val paymentEntity = payment.toEntity(SyncStatus.PENDING_SYNC)
        val serverPayment = payment.copy(id = 100)

        every { NetworkUtils.isOnline() } returns true
        coEvery { paymentDao.insertPayment(any()) } returns 1L
        coEvery { apiService.createPayment(any()) } returns serverPayment
        coEvery { paymentDao.updatePayment(any()) } just Runs

        val result = paymentRepository.createPayment(payment)

        assertTrue(result.isSuccess)
        assertEquals(serverPayment.copy(id = 1), result.getOrNull())

        coVerify { paymentDao.insertPayment(any()) }
        coVerify { apiService.createPayment(any()) }
        coVerify { paymentDao.updatePayment(any()) }
    }

    @Test
    fun `updatePayment updates payment successfully`() = runTest {
        val payment = Payment(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
        )
        val paymentEntity = payment.toEntity(SyncStatus.PENDING_SYNC)

        every { NetworkUtils.isOnline() } returns true
        coEvery { paymentDao.updatePayment(any()) } just Runs
        coEvery { apiService.updatePayment(payment.id, payment) } returns payment

        val result = paymentRepository.updatePayment(payment)

        assertTrue(result.isSuccess)
        assertEquals(payment, result.getOrNull())

        coVerify { paymentDao.updatePayment(any()) }
        coVerify { apiService.updatePayment(payment.id, payment) }
    }

    @Test
    fun `createPaymentWithSplits creates payment and splits successfully`() = runTest {
        val payment = Payment(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
        )

        val splits = listOf(
            PaymentSplit(
                id = 1,
                paymentId = 1,
                userId = 1,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
                ),
            PaymentSplit(
                id = 2,
                paymentId = 1,
                userId = 2,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
            )
        )

        val paymentEntity = payment.toEntity(SyncStatus.PENDING_SYNC)
        val paymentWithId = payment.copy(id = 1)
        val serverPayment = paymentWithId.copy(id = 100)

        coEvery { paymentDao.insertPayment(any()) } returns 1L
        coEvery { paymentSplitDao.insertPaymentSplit(any()) } returnsMany listOf(1L, 2L)
        coEvery { paymentDao.getPaymentById(1) } returns flowOf(paymentEntity)
        every { NetworkUtils.isOnline() } returns true
        coEvery { apiService.createPayment(any()) } returns serverPayment
        coEvery {
            apiService.createPaymentSplit(
                any(),
                any()
            )
        } returnsMany splits.map { it.copy(id = it.userId * 10) }
        coEvery { paymentDao.updatePayment(any()) } just Runs
        coEvery { paymentSplitDao.updatePaymentSplit(any()) } just Runs

        val result = paymentRepository.createPaymentWithSplits(payment, splits)

        assertTrue(result.isSuccess)
        val createdPayment = result.getOrNull()
        assertNotNull(createdPayment)
        assertEquals(1, createdPayment?.id)
        assertEquals(payment.description, createdPayment?.description)

        coVerify { paymentDao.insertPayment(any()) }
        coVerify(exactly = 2) { paymentSplitDao.insertPaymentSplit(any()) }
        coVerify { paymentDao.getPaymentById(1) }
        coEvery { apiService.createPayment(any()) } returns serverPayment

        val slotPaymentId = slot<Int>()
        coVerify(exactly = 2) {
            apiService.createPaymentSplit(
                capture(slotPaymentId),
                any()
            )
        }
        assertEquals(100, slotPaymentId.captured)
        coVerify { paymentDao.updatePayment(any()) }
        coVerify(exactly = 2) { paymentSplitDao.updatePaymentSplit(any()) }
    }

    @Test
    fun `updatePaymentWithSplits updates payment and splits successfully`() = runTest {
        val payment = Payment(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
        )

        val splits = listOf(
            PaymentSplit(
                id = 1,
                paymentId = 1,
                userId = 1,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
            ),
            PaymentSplit(
                id = 2,
                paymentId = 1,
                userId = 2,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
            )
        )

        coEvery { paymentDao.updatePayment(any()) } just Runs
        coEvery { paymentDao.updatePaymentSyncStatus(any(), any()) } just Runs
        coEvery { paymentSplitDao.updatePaymentSplit(any()) } just Runs
        coEvery { paymentSplitDao.insertPaymentSplit(any()) } returns 3L
        coEvery { paymentDao.getPaymentById(1) } returns flowOf(payment.toEntity(SyncStatus.PENDING_SYNC))
        every { NetworkUtils.isOnline() } returns true
        coEvery { apiService.updatePayment(payment.id, any()) } returns payment
        coEvery { apiService.updatePaymentSplit(any(), any(), any()) } returnsMany splits
        coEvery { apiService.createPaymentSplit(any(), any()) } returns splits.last()

        val result = paymentRepository.updatePaymentWithSplits(payment, splits)

        assertTrue(result.isSuccess)
        assertEquals(payment, result.getOrNull())

        coVerify { paymentDao.updatePayment(any()) }
        coVerify { paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.PENDING_SYNC) }
        coVerify(exactly = 4) { paymentSplitDao.updatePaymentSplit(any()) }
        coVerify { paymentDao.getPaymentById(1) }
        coVerify { apiService.updatePayment(payment.id, any()) }
        coVerify(exactly = 2) { apiService.updatePaymentSplit(any(), any(), any()) }
    }

    @Test
    fun `createPaymentSplit creates split successfully`() = runTest {
        val paymentSplit =
            PaymentSplit(
                id = 1,
                paymentId = 1,
                userId = 1,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
            )

        val serverSplit = paymentSplit.copy(id = 100)

        coEvery { paymentSplitDao.insertPaymentSplit(any()) } returns 1L
        coEvery {
            apiService.createPaymentSplit(
                paymentSplit.paymentId,
                paymentSplit
            )
        } returns serverSplit
        coEvery { paymentSplitDao.updatePaymentSplit(any()) } just Runs

        val result = paymentRepository.createPaymentSplit(paymentSplit)

        assertTrue(result.isSuccess)
        assertEquals(serverSplit.copy(id = 1), result.getOrNull())

        coVerify { paymentSplitDao.insertPaymentSplit(any()) }
        coVerify { apiService.createPaymentSplit(paymentSplit.paymentId, paymentSplit) }
        coVerify { paymentSplitDao.updatePaymentSplit(any()) }
    }

    @Test
    fun `updatePaymentSplit updates split successfully`() = runTest {
        val paymentSplit =
            PaymentSplit(
                id = 1,
                paymentId = 1,
                userId = 1,
                amount = 200.0,
                createdBy = 1,
                updatedBy = 1,
                createdAt = timestampNow,
                updatedAt = timestampNow,
                currency = "GBP",
                deletedAt = null,
            )
        coEvery { paymentSplitDao.updatePaymentSplit(any()) } just Runs
        coEvery {
            apiService.updatePaymentSplit(
                paymentSplit.paymentId,
                paymentSplit.id,
                paymentSplit
            )
        } returns paymentSplit

        val result = paymentRepository.updatePaymentSplit(paymentSplit)

        assertTrue(result.isSuccess)
        assertEquals(paymentSplit, result.getOrNull())

        coVerify { paymentSplitDao.updatePaymentSplit(any()) }
        coVerify {
            apiService.updatePaymentSplit(
                paymentSplit.paymentId,
                paymentSplit.id,
                paymentSplit
            )
        }
    }

    @Test
    fun `archivePayment archives payment successfully`() = runTest {
        val paymentId = 1

        coEvery { apiService.archivePayment(paymentId) } just Runs
        coEvery { paymentDao.archivePayment(eq(paymentId), any()) } just Runs

        val result = paymentRepository.archivePayment(paymentId)

        assertTrue(result.isSuccess)

        coVerify { apiService.archivePayment(paymentId) }
        coVerify { paymentDao.archivePayment(eq(paymentId), any()) }
    }

    @Test
    fun `restorePayment restores payment successfully`() = runTest {
        val paymentId = 1

        coEvery { apiService.restorePayment(paymentId) } just Runs
        coEvery { paymentDao.restorePayment(paymentId) } just Runs

        val result = paymentRepository.restorePayment(paymentId)

        assertTrue(result.isSuccess)

        coVerify { apiService.restorePayment(paymentId) }
        coVerify { paymentDao.restorePayment(paymentId) }
    }
}