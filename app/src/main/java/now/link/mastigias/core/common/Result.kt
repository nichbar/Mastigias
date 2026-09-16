package now.link.mastigias.core.common

/**
 * Functional Result wrapper representing Success, Error, or Loading state.
 */
sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Error(val throwable: Throwable? = null, val message: String? = throwable?.message) : Result<Nothing>
    data object Loading : Result<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

/**
 * Extension function to convert a standard library [kotlin.Result] to [Result].
 */
fun <T> kotlin.Result<T>.toAppResult(): Result<T> = fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Error(it) }
)
