/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:25 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi


import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import scala.math._


object SpiFSM extends ChiselEnum {
  val s0IDLE, s1SYNC, s2DATA, s3END, s4DELAY = Value
}

object MODE {
  def NBIT  = 2
  def X     = 0

  def BASE  = 0.U(NBIT.W)
  def DUAL  = 1.U(NBIT.W)
  def QUAD  = 2.U(NBIT.W)
}

object CMD {
  def NBIT  = 2
  def X     = 0

  def NO    = 0.U(NBIT.W)
  def R     = 1.U(NBIT.W)
  def W     = 2.U(NBIT.W)
  def RW    = 3.U(NBIT.W)
}

object IRQ {
  def NBIT  = 2

  def B1    = 0.U(NBIT.W)
  def B2    = 1.U(NBIT.W)
  def B4    = 2.U(NBIT.W)
  def B8    = 3.U(NBIT.W)
}