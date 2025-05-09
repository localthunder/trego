package com.helgolabs.trego.data.exceptions

// Exception thrown when an account sync fails because it's owned by another user
class AccountOwnershipException(message: String) : Exception(message)