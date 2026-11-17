package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals

const val EPS = 1e-9

fun assertVec(expected: Vec2, actual: Vec2) {
    assertEquals("x", expected.x, actual.x, EPS)
    assertEquals("y", expected.y, actual.y, EPS)
}

fun assetId(seed: Char) = AssetId(seed.toString().repeat(64))

fun imageAsset(seed: Char = 'a', width: Int = 200, height: Int = 100) =
    ImageAsset(assetId(seed), "image/png", width, height)

fun imageLayer(id: String, asset: ImageAsset = imageAsset(), transform: Transform = Transform.IDENTITY) =
    ImageLayer(LayerId(id), "Image $id", asset.id, transform)

fun textLayer(id: String, text: String = "Hello") =
    TextLayer(LayerId(id), "Text $id", text, boxWidth = 400.0)

fun canvas() = CanvasSpec(1080, 1080)
