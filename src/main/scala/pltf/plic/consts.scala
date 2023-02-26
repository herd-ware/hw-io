/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:59 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.plic

import chisel3._
import chisel3.util._


// ******************************
//           CONSTANTS
// ******************************
object CST {
  def MAXCONTEXT  = 15872
  def NCAUSE      = 64
  def MAXCAUSE    = 1024
}

// ******************************
//        INTERRUPT TRIGGER
// ******************************
object TRIG {
  def NBIT    = 2

  def PLEVEL  = 0.U(NBIT.W)
  def PEDGE   = 1.U(NBIT.W) 
  def NLEVEL  = 2.U(NBIT.W)
  def NEDGE   = 3.U(NBIT.W) 
}

// ******************************
//          INTERRUPT ID
// ******************************
object ID {
  def ZERO      = 0

  def UART0_TX  = 1
  def UART0_RX  = 2
  def PTIMER0   = 3
  def PTIMER1   = 4
  def PTIMER2   = 5
  def PTIMER3   = 6
  def UART1_TX  = 32
  def UART1_RX  = 33
  def UART2_TX  = 34
  def UART2_RX  = 35
  def UART3_TX  = 36
  def UART3_RX  = 37
}

// ******************************
//        INTERRUPT CAUSE
// ******************************
object CAUSE {
  def USE (
    nUart: Int,
    nPTimer: Int
  ): Array[Boolean] = {
    var use = new Array[Boolean](CST.NCAUSE)

    for (ca <- 0 until CST.NCAUSE) {
      use(ca) = false
    }

    use(ID.UART0_TX)  = (nUart > 0)
    use(ID.UART0_RX)  = (nUart > 0)
    use(ID.PTIMER0)   = (nPTimer > 0)
    use(ID.PTIMER1)   = (nPTimer > 1)
    use(ID.PTIMER2)   = (nPTimer > 0)
    use(ID.PTIMER3)   = (nPTimer > 1)
    use(ID.UART1_TX)  = (nUart > 1)
    use(ID.UART1_RX)  = (nUart > 1)
    use(ID.UART2_TX)  = (nUart > 2)
    use(ID.UART2_RX)  = (nUart > 2)
    use(ID.UART3_TX)  = (nUart > 3)
    use(ID.UART3_RX)  = (nUart > 3)

    return use
  }
}