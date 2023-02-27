/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:12:39 pm                                       *
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

  def useChamp: Boolean
  def nChampTrapLvl: Int

  def nClintPrio: Int = 8
}

case class ClintConfig (
  nDataBit: Int,

  useChamp: Boolean,
  nChampTrapLvl: Int
) extends ClintParams
