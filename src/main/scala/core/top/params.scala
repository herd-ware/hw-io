/*
 * File: params.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 11:33:34 pm
 * Modified By: Mathieu Escouteloup
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
import herd.io.core.hpm._
import herd.io.periph.timer._


trait IOCoreParams extends RegMemParams
                    with ClintParams
                    with HpmParams {
  def pPort: Array[Mb4sParams]

  def debug: Boolean  
  def nHart: Int
  def nAddrBase: String
  def nAddrBit: Int

  def nChampTrapLvl: Int

  def useReqReg: Boolean
  def nScratch: Int  
  def nCTimer: Int 
  def isHpmAct: Array[String]
  def hasHpmMap: Array[String]
  
  def pCTimer: TimerParams = new TimerConfig (
    debug = true
  )
}

case class IOCoreConfig (
  pPort: Array[Mb4sParams],

  debug: Boolean,
  nHart: Int,
  nAddrBit: Int,
  nAddrBase: String,

  nChampTrapLvl: Int,

  useReqReg: Boolean,
  nScratch: Int,  
  nCTimer : Int,
  isHpmAct: Array[String],
  hasHpmMap: Array[String]
) extends IOCoreParams
