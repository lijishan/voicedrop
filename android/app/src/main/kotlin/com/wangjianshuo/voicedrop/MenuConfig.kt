package com.wangjianshuo.voicedrop

object MenuConfig {
    val imageMenu: List<MenuNode> = listOf(
        MenuNode(
            id = "style",
            label = "图片风格",
            type = "submenu",
            children = listOf(
                MenuNode(
                    id = "cartoon",
                    label = "卡通",
                    instruction = "把这张图（[[photo:{{KEY}}]]）重画成宫崎骏动画的手绘卡通风格，构图和主体不变，正文其他内容都不要动。",
                ),
                MenuNode(
                    id = "ad",
                    label = "广告",
                    instruction = "把这张图（[[photo:{{KEY}}]]）重新设计成一则商品广告。请从专业设计师的角度，结合本篇文章的内容和受众，打造一个精致、洗练的视觉设计。整体风格要现代、极简，不使用文字，可以加一些别的代替文字的元素。请通过合理的版式构成，最大限度地突出商品的魅力。正文其他内容都不要动。",
                ),
                MenuNode(
                    id = "watercolor",
                    label = "水彩",
                    instruction = "把这张图（[[photo:{{KEY}}]]）重画成通透的水彩画风格，构图和主体不变，正文其他内容都不要动。",
                ),
                MenuNode(
                    id = "sketch",
                    label = "素描",
                    instruction = "把这张图（[[photo:{{KEY}}]]）重画成铅笔素描风格，构图和主体不变，正文其他内容都不要动。",
                ),
                MenuNode(
                    id = "oil",
                    label = "油画",
                    instruction = "把这张图（[[photo:{{KEY}}]]）重画成古典油画风格，构图和主体不变，正文其他内容都不要动。",
                ),
                MenuNode(
                    id = "film",
                    label = "胶片",
                    instruction = "把这张图（[[photo:{{KEY}}]]）调成胶片摄影的质感和色调，构图和主体不变，正文其他内容都不要动。",
                ),
            ),
        ),
    )

    val textMenu: List<MenuNode> = listOf(
        MenuNode(
            id = "rewrite",
            label = "改写这段",
            type = "submenu",
            children = listOf(
                MenuNode(
                    id = "concise",
                    label = "更简洁",
                    instruction = "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更简洁，意思不变，正文其他行都不要动。",
                ),
                MenuNode(
                    id = "casual",
                    label = "更口语",
                    instruction = "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更口语、像平时说话，意思不变，正文其他行都不要动。",
                ),
                MenuNode(
                    id = "formal",
                    label = "更书面",
                    instruction = "把第{{LINE}}行（开头是\"{{QUOTE}}\"）改写得更书面、更正式，意思不变，正文其他行都不要动。",
                ),
                MenuNode(
                    id = "expand",
                    label = "扩写一点",
                    instruction = "把第{{LINE}}行（开头是\"{{QUOTE}}\"）扩写一点，补充细节但别啰嗦，正文其他行都不要动。",
                ),
            ),
        ),
        MenuNode(
            id = "insert",
            label = "插入图片",
            type = "submenu",
            children = listOf(
                MenuNode(
                    id = "wechat-cover",
                    label = "公众号题图",
                    instruction = "给这篇文章画一张微信公众号题图，放在文章最前面。画面为 2.45:1 的横幅比例。主视觉不要用泛泛的机器人形象或模糊的科技背景，要用具体的物件表达文章主题，比如提示词卡片、设计画布、图片生成面板、封面草稿。题图上的中文主标题从文章标题提炼，必须清晰可读，最好 6 到 10 个汉字。构图要适合公众号封面：大标题放左侧，主视觉放右侧，四周留足安全边距。风格：成熟的新媒体编辑部封面，干净、精致、实用，不要廉价营销海报感。避免：乱码文字、过多小字、真实品牌 logo、纯氛围壁纸、厚重的蓝紫渐变。正文其他内容都不要动。",
                ),
            ),
        ),
    )

    fun fill(instruction: String, subs: Map<String, String>): String {
        var result = instruction
        for ((key, value) in subs) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    fun fillNode(node: MenuNode, subs: Map<String, String>): MenuNode {
        return node.copy(
            instruction = node.instruction?.let { fill(it, subs) },
            children = node.children?.map { fillNode(it, subs) },
        )
    }
}
