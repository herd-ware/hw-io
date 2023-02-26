/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:05 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.timer

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._


trait TimerParams extends GenParams {
  def debug: Boolean

  def useDome: Boolean = false
  def nDome: Int = 1
  def multiDome: Boolean = false
  def nPart: Int = 1
}

case class TimerConfig (
  debug: Boolean
) extends TimerParams
