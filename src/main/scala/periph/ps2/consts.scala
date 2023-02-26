/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:01 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.ps2


import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import scala.math._


object Ps2FSM extends ChiselEnum {
  val s0IDLE, 
      s1RDATA, s2RPARITY, s3RSTOP, s4REND,
      s1SSTART, s2SDATA, s3SPARITY, s4SSTOP, s5SACK, s6SEND = Value
}

object IRQ {
  def NBIT  = 2

  def B1    = 0.U(NBIT.W)
  def B2    = 1.U(NBIT.W)
  def B4    = 2.U(NBIT.W)
  def B8    = 3.U(NBIT.W)
}