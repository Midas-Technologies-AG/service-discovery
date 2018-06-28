/**
 * Copyright 2018 Midas Technologies AG
 */
package social.midas.config

import software.amazon.awssdk.regions.Region

import social.midas.Config

package object aws {

  /**
   * The region we should do queries for as `String` loaded from the
   * `aws.region` attribute from [[config]].
   * 
   * == Default ==
   * 
   * {{{
   * scala> import social.midas.config.aws
   * scala> aws.regionString
   * res0: String = eu-central-1
   * }}}
   */
  lazy val regionString: String = Config.getString("aws.region")

  /**
   * The region we should do queries for as parsed from
   * [[regionString]].
   * 
   * == Default ==
   * 
   * {{{
   * scala> import social.midas.config.aws
   * scala> aws.region
   * res0: software.amazon.awssdk.regions.Region = eu-central-1
   * }}}
   */
  lazy val region: Region = Region.of(regionString)
}
