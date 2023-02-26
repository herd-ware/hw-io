/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:25 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.uart

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._


trait UartParams extends GenParams {
  def debug: Boolean
  def nDataByte: Int
  def nDataBit: Int = nDataByte * 8

  def useDome: Boolean = false
  def nDome: Int = 1
  def multiDome: Boolean = false
  def nPart: Int = 1
  
  def useRegMem: Boolean
  def nBufferDepth: Int
}

case class UartConfig (
  debug: Boolean,
  nDataByte: Int,
  useRegMem: Boolean,
  nBufferDepth: Int
) extends UartParams
