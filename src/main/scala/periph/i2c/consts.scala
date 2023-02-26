/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:42 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.i2c


import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import scala.math._


object I2cFSM extends ChiselEnum {
  val s0IDLE, s1START, s2ADDR, s3RW, s4AACK, s5DATA, s6DACK, s7END, s8STOP, s9DELAY = Value
}

object BIT {
  def IDLE  = 1
  def START = 0
  def ACK   = 0
  def NACK  = 1
  def STOP  = 0
}

object IRQ {
  def NBIT  = 2

  def B1    = 0.U(NBIT.W)
  def B2    = 1.U(NBIT.W)
  def B4    = 2.U(NBIT.W)
  def B8    = 3.U(NBIT.W)
}