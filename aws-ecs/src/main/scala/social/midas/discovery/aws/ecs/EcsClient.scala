/**
 * Copyright 2018 Midas Technologies AG.
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
package social.midas.discovery.aws.ecs

import java.util.concurrent.CompletableFuture

import scala.collection.JavaConverters._
import scala.util.matching.Regex

import cats.effect.IO
import org.apache.logging.log4j.scala.Logging
import social.midas.discovery.common.{AbstractContext, aws}
import social.midas.discovery.common.aws.{Arn, ArnLike, AwsClient}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.{EcsAsyncClient, EcsAsyncClientBuilder}
import software.amazon.awssdk.services.ecs.model.{
  DescribeTasksRequest, ListClustersResponse,
  ListServicesRequest, ListServicesResponse,
  ListTasksRequest, ListTasksResponse,
}

/**
 * A wrapper for
 * [[software.amazon.awssdk.services.ecs.ECSAsyncClient]].
 */
final case class EcsClient(region: Region)
    extends AwsClient[EcsAsyncClientBuilder, EcsAsyncClient]
    with Logging {

  def builder = EcsAsyncClient.builder()

  /**
   * List cluster ARNs and filter the result by `filterArn` if
   * provided. Note that filtering happens post-fetch.
   */
  def listClusters(filterArn: Option[Regex] = None)
      : IO[Seq[EcsClusterArn]] =
    queryListExtractTransformMatch[ListClustersResponse, EcsClusterArn](
      _.listClusters(),
      _.clusterArns,
      EcsClusterArn(_),
      filterArn
    )

  /**
   * List service ARNs related to `cluster` and filter the result by
   * `filterArn`. Note that filtering happens post-fetch.
   */
  def listServices(
    cluster: EcsClusterArn,
    filterArn: Option[Regex] = None,
  ) : IO[Seq[EcsServiceArn]] = {
    val request = ListServicesRequest.builder().cluster(cluster.arn.unwrap).build()
    queryListExtractTransformMatch[ListServicesResponse, EcsServiceArn](
      _.listServices(request),
      _.serviceArns,
      x => EcsServiceArn(Arn(x), cluster),
      filterArn,
    )
  }

  /**
   * List task ARNs related to `cluster` and filter the result by
   * `filterArn`. Filtering happens post-fetch. Only fetches tasks in
   * `family` if provided.
   */
  def listTasks(
    cluster: EcsClusterArn,
    filterArn: Option[Regex] = None,
    family: Option[String] = None,
  ) : IO[Seq[EcsTaskArn]] = {
    val builder = ListTasksRequest.builder()
      .cluster(cluster.arn.unwrap)
    val request = family.map(builder.family(_)).getOrElse(builder)
        .build()

    queryListExtractTransformMatch[ListTasksResponse, EcsTaskArn](
      _.listTasks(request),
      _.taskArns,
      x => EcsTaskArn(Arn(x), cluster),
      filterArn,
    )
  }

  /**
   * Fetches descriptions of `tasks` in `cluster`.
   */
  def describeTasks(cluster: EcsClusterArn, tasks: Seq[Arn])
      : IO[Seq[EcsTask]] = {
    val request = DescribeTasksRequest.builder()
      .cluster(cluster.arn.unwrap)
      .tasks(tasks.map(_.arn.unwrap).asJava)
      .build()
    withClient(_.describeTasks(request)).map(
      _.tasks.asScala.toSeq.map(EcsTask.apply)
    )
  }

  /**
   * Common logic needed in other functions: fetch a list of elements
   * via `list`, `extract` the results and transform them to type `T`
   * which is [[social.midas.discovery.common.aws.ArnLike]]. Filter these by
   * `maybeRegex` if provided.
   */
  protected def queryListExtractTransformMatch[R,T <: ArnLike](
    list: EcsAsyncClient => CompletableFuture[R],
    extract: R => java.util.List[String],
    transform: String => T,
    maybeRegex: Option[Regex] = None,
  ): IO[Seq[T]] = {
    withClient(list).map({ response =>
      val extracted = extract(response)
      val transformed = extracted.asScala.toSeq.map(transform)
      maybeRegex match {
        case None => transformed
        case Some(r) => transformed.filter(_.arnMatches(r))
      }
    })
  }
}

object EcsClient {
  def apply(ctx: AbstractContext): EcsClient =
    EcsClient(aws.regionFromContext(ctx))

  def apply(): EcsClient =
    EcsClient(aws.region())
}
