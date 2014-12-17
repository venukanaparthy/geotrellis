package geotrellis.spark

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.spark.tiling._

import org.apache.spark._

trait RasterRDDBuilders {

  lazy val defaultCRS = LatLng

  def createRasterRDD(
    sc: SparkContext,
    raster: Tile,
    tileLayout: TileLayout,
    cellType: CellType = TypeInt): RasterRDD[SpatialKey] = {

    val extent = defaultCRS.worldExtent

    val metaData = RasterMetaData(
      cellType,
      extent,
      defaultCRS,
      tileLayout
    )

    val re = RasterExtent(
      extent = extent,
      cols = tileLayout.layoutCols,
      rows = tileLayout.layoutRows
    )

    val tileBounds = re.gridBoundsFor(extent)

    val adjustedRaster =
      if (raster.cols == tileLayout.totalCols.toInt &&
        raster.rows == tileLayout.totalRows.toInt) raster
      else CompositeTile.wrap(raster, tileLayout, cropped = false)

    val tmsTiles =
      tileBounds.coords.map { case (col, row) =>

        val targetRasterExtent =
          RasterExtent(
            extent = re.extentFor(GridBounds(col, row, col, row)),
            cols = tileLayout.tileCols,
            rows = tileLayout.tileRows
          )

        val subTile: Tile = adjustedRaster.warp(extent, targetRasterExtent)

        (SpatialKey(col, row), subTile)
      }


    asRasterRDD(metaData) {
      sc.parallelize(tmsTiles)
    }
  }

}