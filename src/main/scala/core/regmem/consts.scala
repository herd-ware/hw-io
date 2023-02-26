/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:07:07 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.regmem

import chisel3._
import chisel3.util._


// ******************************
//           CONSTANTS
// ******************************
object CST {
  def MAXCTIMER     = 4
  def MAXSCRATCH    = 32
}

// ******************************
//        REGISTER ADDRESSES
// ******************************
object COMMON {
  def NBIT = 12    

  // Core timer
  def CTIMER_NBYTE      = 0x20
  def CTIMER_STATUS     = "h100"
  def CTIMER_CONFIG     = "h104"
  def CTIMER_CNT        = "h110"
  def CTIMER_CNTH       = "h114"
  def CTIMER_CMP        = "h118"
  def CTIMER_CMPH       = "h11c"

  // Scratch
  def SCRATCH           = "h200"
}

object PRIV {
  // Machine timer
  def MTIMER_NBYTE      = 0x20
  def MTIMER_STATUS     = "h000"
  def MTIMER_CONFIG     = "h004"
  def MTIMER_CNT        = "h010"
  def MTIMER_CNTH       = "h014"
  def MTIMER_CMP        = "h018"
  def MTIMER_CMPH       = "h01c"
}

object CEPS {
  // Level 0 timer
  def L0TIMER_NBYTE      = 0x20
  def L0TIMER_STATUS     = "h000"
  def L0TIMER_CONFIG     = "h004"
  def L0TIMER_CNT        = "h010"
  def L0TIMER_CNTH       = "h014"
  def L0TIMER_CMP        = "h018"
  def L0TIMER_CMPH       = "h01c"

  // Level 1 timer
  def L1TIMER_NBYTE      = 0x20
  def L1TIMER_STATUS     = "h020"
  def L1TIMER_CONFIG     = "h024"
  def L1TIMER_CNT        = "h030"
  def L1TIMER_CNTH       = "h034"
  def L1TIMER_CMP        = "h038"
  def L1TIMER_CMPH       = "h03c"
}