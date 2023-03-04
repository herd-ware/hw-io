/*
 * File: params.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 09:34:03 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.hpm

import chisel3._
import chisel3.experimental.IO
import chisel3.util._


trait HpmParams {
  def debug: Boolean
  def nHart: Int

  def isHpmAct: Array[String]
  def hasHpmMap: Array[String]
  def nHpmMap: Int = hasHpmMap.size
}

case class HpmConfig (
  debug: Boolean,
  nHart: Int, 

  isHpmAct: Array[String],
  hasHpmMap: Array[String]
) extends HpmParams
