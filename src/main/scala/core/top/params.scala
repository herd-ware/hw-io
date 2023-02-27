/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:20:14 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.mem.mb4s._
import herd.io.core.regmem._
import herd.io.core.clint._
import herd.io.periph.timer._


trait IOCoreParams extends RegMemParams
                    with ClintParams {
  def pPort: Array[Mb4sParams]

  def debug: Boolean  
  def nAddrBase: String
  def nAddrBit: Int

  def nChampTrapLvl: Int

  def useReqReg: Boolean
  def nScratch: Int  
  def nCTimer: Int 
  
  def pCTimer: TimerParams = new TimerConfig (
    debug = true
  )
}

case class IOCoreConfig (
  pPort: Array[Mb4sParams],

  debug: Boolean,
  nAddrBit: Int,
  nAddrBase: String,

  nChampTrapLvl: Int,

  useReqReg: Boolean,
  nScratch: Int,  
  nCTimer : Int
) extends IOCoreParams
