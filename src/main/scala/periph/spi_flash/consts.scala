/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:43 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi_flash


import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import scala.math._


object SpiFlashFSM extends ChiselEnum {
  val s0IDLE, s1ACMD, s2AADDR, s3AREAD, s4DATA = Value
}

object CMD {
  def NBIT  = 3
  def X     = 0

  def NO    = 0.U(NBIT.W)
  def R     = 1.U(NBIT.W)
  def W     = 2.U(NBIT.W)
  def RW    = 3.U(NBIT.W)
  def RA    = 5.U(NBIT.W)
}

object IRQ {
  def NBIT  = 2

  def B1    = 0.U(NBIT.W)
  def B2    = 1.U(NBIT.W)
  def B4    = 2.U(NBIT.W)
  def B8    = 3.U(NBIT.W)
}

object FLASH {
  def NBIT  = 8

  def READ  = 3.U(NBIT.W)
}