/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:03 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.plic

import chisel3._
import chisel3.experimental.IO
import chisel3.util._


trait PlicParams {  
  def nDataBit: Int

  def nPlicContext: Int
  def nPlicContext32b: Int = {
    if (nDataBit >= 64) {
      return (nPlicContext + 64 - 1) / 32
    } else {
      return (nPlicContext + 32 - 1) / 32
    }
  }
  def nPlicContext64b: Int = (nPlicContext + 64 - 1) / 64
  def nPlicCause: Int = CST.NCAUSE
  def nPlicCause32b: Int = {
    if (nDataBit >= 64) {
      return (nPlicCause + 64 - 1) / 32
    } else {
      return (nPlicCause + 32 - 1) / 32
    }
  }
  def nPlicCause64b: Int = (nPlicCause + 64 - 1) / 64
  def nPlicCauseUse: Array[Boolean] 
  def nPlicPrio: Int
}

case class PlicConfig (
  nDataBit: Int,

  nPlicContext: Int,
  nPlicCauseUse: Array[Boolean],
  nPlicPrio: Int
) extends PlicParams
