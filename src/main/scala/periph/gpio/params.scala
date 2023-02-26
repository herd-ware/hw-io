/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:29 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.gpio

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._


trait GpioParams extends GenParams {
  def debug: Boolean

  def useDome: Boolean = false
  def nDome: Int = 1
  def multiDome: Boolean = false
  def nPart: Int = 1

  def nGpio: Int
  def nGpio32b: Int = (nGpio + 32 - 1) / 32 
}

case class GpioConfig (
  debug: Boolean,
  nGpio: Int
) extends GpioParams
