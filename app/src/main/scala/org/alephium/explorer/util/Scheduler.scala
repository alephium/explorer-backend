// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer.util

import java.time.{OffsetTime, ZonedDateTime}
import java.util.{Timer, TimerTask}

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer._
import org.alephium.explorer.util.Scheduler.scheduleTime

object Scheduler extends StrictLogging {

  /**
    * Creates a Scheduler.
    *
    * @param name     Name of the associated thread
    * @param isDaemon `true` if the associated thread should run as a daemon
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply(name: String, isDaemon: Boolean = true): Scheduler =
    new Scheduler(
      name       = name,
      timer      = new Timer(name, isDaemon),
      terminated = false
    )

  /**
    * Calculate time left to schedule for the input [[java.time.ZonedDateTime]].
    * If the time is in the past then it schedules for tomorrow.
    *
    * @param scheduleAt Time to schedule at.
    *
    * @return Time left until next schedule.
    */
  @tailrec
  def scheduleTime(scheduleAt: ZonedDateTime, id: String): FiniteDuration = {
    import java.time.Duration

    val timeLeft = Duration.between(ZonedDateTime.now(scheduleAt.getZone), scheduleAt)

    if (timeLeft.isNegative) { //time is in the past, schedule for tomorrow.
      val daysBehind = Math.abs(timeLeft.toDays) + 1
      logger.trace(s"$id: Scheduled time is $daysBehind.days behind")
      scheduleTime(scheduleAt.plusDays(daysBehind), id)
    } else { //time is in the future. Good!
      val nextSchedule = timeLeft.toNanos.nanos
      //calculate first schedule using today's date.
      logger.debug(s"$id: Scheduled delay ${nextSchedule.toSeconds}.seconds")
      nextSchedule
    }
  }
}

class Scheduler private (name: String, timer: Timer, @volatile private var terminated: Boolean)
    extends AutoCloseable
    with StrictLogging {

  /** Prefix used in logs to differentiate schedulers and tasks submitted to the scheduler */
  @inline def logId(taskId: String): String =
    s"Scheduler '$name', Task '$taskId'"

  /**
    * Schedules the block after a delay.
    *
    * Every other function is just a combinator to build more functionality
    * on top of this function.
    */
  def scheduleOnce[T](taskId: String, delay: FiniteDuration)(block: => Future[T]): Future[T] = {
    val promise = Promise[T]()

    val task =
      new TimerTask {
        def run(): Unit =
          sideEffect(promise.completeWith(block))
      }

    timer.schedule(task, delay.toMillis max 0)
    logger.debug(
      s"${logId(taskId)}: Scheduled with delay ${delay.toSeconds}.seconds"
    )
    promise.future
  }

  /** Schedule block at given interval */
  def scheduleLoop[T](taskId: String, interval: FiniteDuration)(block: => Future[T])(
      implicit ec: ExecutionContext): Future[T] =
    scheduleLoop(taskId, interval, interval)(block)

  /** Fires and forgets the scheduled task */
  def scheduleLoopAndForget[T](taskId: String, interval: FiniteDuration)(block: => Future[T])(
      implicit ec: ExecutionContext): Unit =
    sideEffect(scheduleLoop(taskId, interval, interval)(block))

  /** Schedules the block at given `loopInterval` with the first schedule at `firstInterval` */
  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleLoop[T](taskId: String, firstInterval: FiniteDuration, loopInterval: FiniteDuration)(
      block: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val initial = scheduleOnce(taskId, firstInterval)(block)

    initial onComplete {
      case Failure(exception) =>
        //Log the failure.
        logger.error(s"${logId(taskId)}: Failed executing task", exception)
        if (!terminated) {
          scheduleLoop(taskId, loopInterval, loopInterval)(block)
        }

      case Success(_) =>
        if (!terminated) {
          scheduleLoop(taskId, loopInterval, loopInterval)(block)
        }
    }

    initial
  }

  /**
    * Schedules daily, starting from the given [[java.time.ZonedDateTime]].
    *
    * If the time is in the past (eg: 1PM when now is 2PM) then the
    * schedule occurs for tomorrow.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleDailyAt[T](taskId: String, at: ZonedDateTime)(block: => Future[T])(
      implicit ec: ExecutionContext): Unit =
    scheduleOnce(taskId, scheduleTime(at, logId(taskId)))(block) onComplete {
      case Failure(exception) =>
        //Log the failure.
        logger.error(s"${logId(taskId)}: Failed executing task", exception)
        if (!terminated) {
          scheduleDailyAt(taskId, at)(block)
        }

      case Success(_) =>
        if (!terminated) {
          scheduleDailyAt(taskId, at)(block)
        }
    }

  /**
    * Schedules daily, starting from today.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def scheduleDailyAt[T](taskId: String, at: OffsetTime)(block: => Future[T])(
      implicit ec: ExecutionContext): Unit =
    scheduleDailyAt(taskId, TimeUtil.toZonedDateTime(at))(block)

  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleLoopConditional[A, S](
      taskId: String,
      interval: FiniteDuration,
      state: S
  )(init: => Future[Boolean])(block: S => Future[A])(implicit ec: ExecutionContext): Unit =
    scheduleLoopConditional(
      taskId        = taskId,
      firstInterval = interval,
      loopInterval  = interval,
      state         = state
    )(init)(block)

  /**
    * Conditionally executes `block` only if `init` returns true.
    * @param state The state of the block.
    *              Unlike `scheduleLoopFlatMap` above which passes the result of `init`
    *              as state into `block`, here the state is provided as a function input.
    * @param init  Invoked at specified intervals until it returns true.
    *              When true, block is executed without any further init
    *              invocations.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleLoopConditional[A, S](
      taskId: String,
      firstInterval: FiniteDuration,
      loopInterval: FiniteDuration,
      state: S
  )(init: => Future[Boolean])(block: S => Future[A])(implicit ec: ExecutionContext): Unit = {
    //false if init has not been executed or previous init attempt returned false, else true.
    @volatile var initialized: Boolean = false

    sideEffect {
      scheduleLoop(
        taskId        = taskId,
        firstInterval = firstInterval,
        loopInterval  = loopInterval
      ) {
        if (initialized) { //Already initialised. Invoke block!
          block(state)
        } else { //Not initialised! Invoke init!
          init flatMap { initResult =>
            if (initResult) { //Init successful! Invoke block!
              logger.debug(
                s"${logId(taskId)}: Task initialisation result: $initResult. Executing block")
              initialized = initResult
              block(state)
            } else {
              //Init returned false. Do not execute block.
              logger.debug(
                s"${logId(taskId)}: Task initialisation result: $initResult. Executing block delayed.")
              Future.unit
            }
          }
        }
      }
    }
  }

  override def close(): Unit = {
    terminated = true
    timer.cancel()
    logger.info(s"Scheduler '$name' terminated!")
  }
}
