package com.bridginghelp.core.common.result

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Result 类的单元测试
 */
class ResultTest {

    @Test
    fun `Success should contain data`() {
        val data = "test data"
        val result = Result.Success(data)

        assertTrue(result is Result.Success)
        assertEquals(data, result.data)
        assertNull(result.errorOrNull())
    }

    @Test
    fun `Error should contain error`() {
        val error = ErrorEntity.Unknown("test error")
        val result = Result.Error(error)

        assertTrue(result is Result.Error)
        assertEquals(error, result.error)
        assertNull(result.getOrNull())
    }

    @Test
    fun `Loading should be loading state`() {
        val result = Result.Loading

        assertTrue(result is Result.Loading)
        assertNull(result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun `isSuccess should return true for Success`() {
        val result = Result.Success("data")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `isSuccess should return false for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("error"))
        assertFalse(result.isSuccess)
    }

    @Test
    fun `isSuccess should return false for Loading`() {
        val result = Result.Loading
        assertFalse(result.isSuccess)
    }

    @Test
    fun `isError should return true for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("error"))
        assertTrue(result.isError)
    }

    @Test
    fun `isError should return false for Success`() {
        val result = Result.Success("data")
        assertFalse(result.isError)
    }

    @Test
    fun `isError should return false for Loading`() {
        val result = Result.Loading
        assertFalse(result.isError)
    }

    @Test
    fun `isLoading should return true for Loading`() {
        val result = Result.Loading
        assertTrue(result.isLoading)
    }

    @Test
    fun `isLoading should return false for Success`() {
        val result = Result.Success("data")
        assertFalse(result.isLoading)
    }

    @Test
    fun `isLoading should return false for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("error"))
        assertFalse(result.isLoading)
    }

    @Test
    fun `getOrNull should return data for Success`() {
        val data = "test data"
        val result = Result.Success(data)
        assertEquals(data, result.getOrNull())
    }

    @Test
    fun `getOrNull should return null for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("error"))
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrNull should return null for Loading`() {
        val result = Result.Loading
        assertNull(result.getOrNull())
    }

    @Test
    fun `map should transform Success data`() {
        val result = Result.Success(5)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is Result.Success)
        val successResult = mapped as Result.Success
        assertEquals(10, successResult.data)
    }

    @Test
    fun `map should preserve Error`() {
        val error = ErrorEntity.Unknown("error")
        val result: Result.Error = Result.Error(error)
        val mapped = result.map { it: Int -> it * 2 }

        assertTrue(mapped is Result.Error)
        val errorResult = mapped as Result.Error
        assertEquals(error, errorResult.error)
    }

    @Test
    fun `flatMap should work with Success`() {
        val result = Result.Success(5)
        val flatMapped = result.flatMap { Result.Success(it * 2) }

        assertTrue(flatMapped is Result.Success)
        val successResult = flatMapped as Result.Success
        assertEquals(10, successResult.data)
    }

    @Test
    fun `flatMap should preserve Error`() {
        val error = ErrorEntity.Unknown("error")
        val result: Result.Error = Result.Error(error)
        val flatMapped = result.flatMap { _ -> Result.Success(10) }

        assertTrue(flatMapped is Result.Error)
        val errorResult = flatMapped as Result.Error
        assertEquals(error, errorResult.error)
    }

    @Test
    fun `catch should create Success`() {
        val result = Result.catch { "test data" }
        assertTrue(result is Result.Success)
        val successResult = result as Result.Success
        assertEquals("test data", successResult.data)
    }

    @Test
    fun `catch should create Error on exception`() {
        val exception = RuntimeException("test exception")
        val result = Result.catch<String> { throw exception }

        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals(ErrorEntity.fromException(exception), errorResult.error)
    }

    @Test
    fun `onSuccess should execute for Success`() {
        val result = Result.Success("data")
        var executed = false
        var receivedData = ""

        result.onSuccess { data ->
            executed = true
            receivedData = data
        }

        assertTrue(executed)
        assertEquals("data", receivedData)
    }

    @Test
    fun `onSuccess should not execute for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("error"))
        var executed = false

        result.onSuccess { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onError should execute for Error`() {
        val result = Result.Error(ErrorEntity.Unknown("test error"))
        var executed = false
        var receivedMessage = ""

        result.onError { error ->
            executed = true
            receivedMessage = error.message
        }

        assertTrue(executed)
        assertEquals("test error", receivedMessage)
    }

    @Test
    fun `onError should not execute for Success`() {
        val result = Result.Success("data")
        var executed = false

        result.onError { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onLoading should execute for Loading`() {
        val result = Result.Loading
        var executed = false

        result.onLoading { executed = true }

        assertTrue(executed)
    }

    @Test
    fun `onLoading should not execute for Success`() {
        val result = Result.Success("data")
        var executed = false

        result.onLoading { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `ErrorEntity message should extract message`() {
        val error = ErrorEntity.Unknown("test message")
        assertEquals("test message", error.message)
    }

    @Test
    fun `ErrorEntity fromException should create Unknown`() {
        val exception = RuntimeException("test exception")
        val error = ErrorEntity.fromException(exception)

        assertTrue(error is ErrorEntity.Unknown)
        assertEquals("test exception", error.message)
    }

    @Test
    fun `ErrorEntity fromException should create Validation for IllegalArgumentException`() {
        val exception = IllegalArgumentException("validation failed")
        val error = ErrorEntity.fromException(exception)

        assertTrue(error is ErrorEntity.Validation)
        assertEquals("validation failed", error.message)
    }

    @Test
    fun `ErrorEntity fromException should create Permission for SecurityException`() {
        val exception = SecurityException("permission denied")
        val error = ErrorEntity.fromException(exception)

        assertTrue(error is ErrorEntity.Permission)
    }
}
