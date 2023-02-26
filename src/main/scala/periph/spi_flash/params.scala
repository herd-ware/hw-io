/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:47 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi_flash

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._


trait SpiFlashParams extends GenParams {
  def debug: Boolean
  def nByte: String = "01000000"
  def nAddrBit: Int = log2Ceil(BigInt(nByte, 16))
  def nDataByte: Int
  def nDataBit: Int = nDataByte * 8

  def useDome: Boolean = false
  def nDome: Int = 1
  def multiDome: Boolean = false
  def nPart: Int = 1
  
  def useRegMem: Boolean
  def useDma: Boolean
  def nBufferDepth: Int
}

case class SpiFlashConfig (
  debug: Boolean,
  nDataByte: Int,
  useRegMem: Boolean,
  useDma: Boolean,
  nBufferDepth: Int
) extends SpiFlashParams
