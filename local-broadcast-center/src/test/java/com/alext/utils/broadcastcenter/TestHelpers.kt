package com.alext.utils.broadcastcenter

import junit.framework.TestCase
import org.junit.Assert

@Suppress("FunctionNaming")
infix fun <T> T.should_be(expected: T) {
    TestCase.assertEquals(expected, this)
}

@Suppress("FunctionNaming")
infix fun <T> T.should_not_be(expected: T) {
    Assert.assertNotEquals(expected, this)
}