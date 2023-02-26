/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:06:55 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.clint

import chisel3._
import chisel3.experimental.IO
import chisel3.util._


trait ClintParams {
  def nDataBit: Int

  def useCeps: Boolean
  def nCepsTrapLvl: Int

  def nClintPrio: Int = 8
}

case class ClintConfig (
  nDataBit: Int,

  useCeps: Boolean,
  nCepsTrapLvl: Int
) extends ClintParams
