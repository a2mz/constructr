/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.constructr.coordination

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCode }
import akka.stream.Materializer
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

object Coordination {

  sealed trait Backend {
    type Context
  }

  object Backend {
    case object Etcd extends Backend {
      override type Context = None.type
    }
    case object Consul extends Backend {
      type SessionId = String
      override type Context = SessionId
    }
  }

  trait AddressSerialization[A] {
    def fromBytes(bytes: Array[Byte]): A
    def toBytes(a: A): Array[Byte]
  }

  sealed trait LockResult
  object LockResult {
    case object Success extends LockResult
    case object Failure extends LockResult
  }

  case class SelfAdded[B <: Coordination.Backend](context: B#Context)

  case class Refreshed[B <: Coordination.Backend](context: B#Context)

  case class UnexpectedStatusCode(statusCode: StatusCode) extends RuntimeException(s"Unexpected status code $statusCode!")

  def apply[B <: Coordination.Backend](backend: Backend)(prefix: String, clusterName: String, host: String, port: Int, send: HttpRequest => Future[HttpResponse]): Coordination[B] =
    backend match {
      case Backend.Etcd   => new EtcdCoordination(prefix, clusterName, host, port, send).asInstanceOf[Coordination[B]]
      case Backend.Consul => new ConsulCoordination(prefix, clusterName, host, port, send).asInstanceOf[Coordination[B]]
    }
}

abstract class Coordination[B <: Coordination.Backend](prefix: String, clusterName: String, host: String, port: Int, send: HttpRequest => Future[HttpResponse]) {
  import Coordination._

  def getNodes[A: AddressSerialization]()(implicit ec: ExecutionContext, mat: Materializer): Future[List[A]]

  def lock(ttl: Duration)(implicit ec: ExecutionContext, mat: Materializer): Future[LockResult]

  def addSelf[A: AddressSerialization](self: A, ttl: Duration)(implicit ec: ExecutionContext, mat: Materializer): Future[SelfAdded[B]]

  def refresh[A: AddressSerialization](self: A, ttl: Duration, context: B#Context)(implicit ec: ExecutionContext, mat: Materializer): Future[Refreshed[B]]

  def initialBackendContext: B#Context
}