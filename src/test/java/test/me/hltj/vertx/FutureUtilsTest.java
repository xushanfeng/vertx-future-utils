/*
 * vertx-future-utils - Convenient Utilities for Vert.x Future
 * https://github.com/hltj/vertx-future-utils
 *
 * Copyright (C) 2020  JiaYanwei  https://hltj.me
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Please contact me (jiaywe#at#gmail.com, replace the '#at#' with 'at')
 * if you need additional information or have any questions.
 */
package test.me.hltj.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import lombok.SneakyThrows;
import lombok.val;
import me.hltj.vertx.FutureUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static test.me.hltj.vertx.SharedTestUtils.assertSucceedWith;

class FutureUtilsTest {

    @SneakyThrows
    @Test
    void futurize() {
        val future0 = FutureUtils.<Integer>futurize(handler -> delayParseInt("1", handler)).map(i -> i + 1);
        val future1 = FutureUtils.<Integer>futurize(handler -> delayParseInt("%", handler)).map(i -> i + 1);

        CountDownLatch latch = new CountDownLatch(1);
        CompositeFuture.join(future0, future1).onComplete(future -> {
            assertSucceedWith(2, future0);
            assertFailedWith(NumberFormatException.class, "For input string: \"%\"", future1);
            latch.countDown();
        });

        latch.await();
    }

    @Test
    void defaultWith() {
        assertSucceedWith("value", FutureUtils.defaultWith(Future.succeededFuture("value"), "default"));
        assertSucceedWith("default", FutureUtils.defaultWith(Future.succeededFuture(), "default"));
        assertFailedWith("error", FutureUtils.defaultWith(Future.failedFuture("error"), "default"));
    }

    @Test
    void defaultWith_supplier() {
        val numbers = new HashSet<Integer>();

        assertSucceedWith("value", FutureUtils.<String>defaultWith(Future.succeededFuture("value"), () -> {
            numbers.add(0);
            return "default";
        }));
        assertFalse(numbers.contains(0));

        assertSucceedWith("default", FutureUtils.<String>defaultWith(Future.succeededFuture(), () -> {
            numbers.add(1);
            return "default";
        }));
        assertTrue(numbers.contains(1));

        assertFailedWith("error", FutureUtils.<String>defaultWith(Future.failedFuture("error"), () -> {
            numbers.add(2);
            return "default";
        }));
        assertFalse(numbers.contains(2));
    }

    @Test
    void fallbackWith() {
        assertSucceedWith("value", FutureUtils.fallbackWith(Future.succeededFuture("value"), "fallback"));
        assertSucceedWith("fallback", FutureUtils.fallbackWith(Future.succeededFuture(), "fallback"));
        assertSucceedWith("fallback", FutureUtils.fallbackWith(Future.failedFuture("error"), "fallback"));
    }

    @Test
    void fallbackWith_function() {
        val throwables = new ArrayList<Throwable>();
        val numbers = new HashSet<Integer>();

        assertSucceedWith("value", FutureUtils.<String>fallbackWith(Future.succeededFuture("value"), opt -> {
            opt.ifPresent(throwables::add);
            numbers.add(0);
            return "fallback";
        }));
        assertTrue(throwables.isEmpty());
        assertFalse(numbers.contains(0));

        assertSucceedWith("fallback", FutureUtils.<String>fallbackWith(Future.succeededFuture(), opt -> {
            opt.ifPresent(throwables::add);
            numbers.add(1);
            return "fallback";
        }));
        assertTrue(throwables.isEmpty());
        assertTrue(numbers.contains(1));

        assertSucceedWith("fallback", FutureUtils.<String>fallbackWith(Future.failedFuture("error"), opt -> {
            opt.ifPresent(throwables::add);
            numbers.add(2);
            return "fallback";
        }));
        assertFalse(throwables.isEmpty());
        assertTrue(numbers.contains(2));
    }

    @Test
    void fallbackWith_mapper_supplier() {
        val throwables = new ArrayList<Throwable>();
        val numbers = new HashSet<Integer>();

        assertSucceedWith("value", FutureUtils.fallbackWith(Future.succeededFuture("value"), t -> {
            throwables.add(t);
            return "otherwise";
        }, () -> {
            numbers.add(0);
            return "default";
        }));
        assertTrue(throwables.isEmpty());
        assertFalse(numbers.contains(0));

        assertSucceedWith("default", FutureUtils.fallbackWith(Future.succeededFuture(), t -> {
            throwables.add(t);
            return "otherwise";
        }, () -> {
            numbers.add(1);
            return "default";
        }));
        assertTrue(throwables.isEmpty());
        assertTrue(numbers.contains(1));

        assertSucceedWith("otherwise", FutureUtils.fallbackWith(Future.failedFuture("error"), t -> {
            throwables.add(t);
            return "otherwise";
        }, () -> {
            numbers.add(2);
            return "default";
        }));
        assertFalse(throwables.isEmpty());
        assertFalse(numbers.contains(2));
    }

    @Test
    void wrap() {
        assertSucceedWith(1, FutureUtils.wrap(() -> Integer.parseInt("1")));
        assertFailedWith(NumberFormatException.class, "For input string: \"@\"",
                FutureUtils.wrap(() -> Integer.parseInt("@"))
        );
    }

    @Test
    void wrap_function() {
        assertSucceedWith(1, FutureUtils.wrap("1", Integer::parseInt));
        assertFailedWith(
                NumberFormatException.class, "For input string: \"@\"",
                FutureUtils.wrap("@", Integer::parseInt)
        );
    }

    @Test
    void joinWrap_flatWrap() {
        Future<Integer> future0 = FutureUtils.wrap("0", Integer::parseInt);
        Future<Integer> future1 = FutureUtils.wrap("1", Integer::parseInt);

        assertFailedWith(
                ArithmeticException.class, "/ by zero",
                FutureUtils.joinWrap(() -> future0.map(i -> 2 / i))
        );
        assertFailedWith(
                ArithmeticException.class, "/ by zero",
                FutureUtils.flatWrap(() -> future0.map(i -> 2 / i))
        );

        assertSucceedWith(2, FutureUtils.joinWrap(() -> future1.map(i -> 2 / i)));
        assertSucceedWith(2, FutureUtils.flatWrap(() -> future1.map(i -> 2 / i)));

        //noinspection ConstantConditions
        SharedTestUtils.assertFailedWith(
                NullPointerException.class,
                FutureUtils.joinWrap(() -> ((Future<Integer>) null).map(i -> 2 / i))
        );
        //noinspection ConstantConditions
        SharedTestUtils.assertFailedWith(
                NullPointerException.class,
                FutureUtils.flatWrap(() -> ((Future<Integer>) null).map(i -> 2 / i))
        );
    }

    @Test
    void joinWrap_flatWrap_function() {
        Function<String, Future<Integer>> stringToIntFuture = s -> FutureUtils.wrap(s, Integer::parseInt);

        assertSucceedWith(1, FutureUtils.joinWrap("1", stringToIntFuture));
        assertSucceedWith(1, FutureUtils.flatWrap("1", stringToIntFuture));

        assertFailedWith(
                NumberFormatException.class, "For input string: \"!\"",
                FutureUtils.joinWrap("!", stringToIntFuture)
        );
        assertFailedWith(
                NumberFormatException.class, "For input string: \"!\"",
                FutureUtils.flatWrap("!", stringToIntFuture)
        );

        assertFailedWith(NumberFormatException.class, "null", FutureUtils.joinWrap(null, stringToIntFuture));
        assertFailedWith(NumberFormatException.class, "null", FutureUtils.flatWrap(null, stringToIntFuture));
    }

    @SneakyThrows
    private static void delayParseInt(String s, Handler<AsyncResult<Integer>> handler) {
        Thread.sleep(1_000);
        handler.handle(FutureUtils.wrap(s, Integer::parseInt));
    }

    @SuppressWarnings("SameParameterValue")
    private static <T> void assertFailedWith(String expectedMessage, Future<T> actual) {
        assertTrue(actual.failed());
        assertEquals(expectedMessage, actual.cause().getMessage());
    }

    private static <T, E> void assertFailedWith(Class<E> clazz, String expectedMessage, Future<T> actual) {
        assertTrue(actual.failed());
        assertTrue(clazz.isInstance(actual.cause()));
        assertEquals(expectedMessage, actual.cause().getMessage());
    }
}
