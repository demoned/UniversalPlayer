package com.demons.player

class DataSource {
    var currentUrlIndex: Int
    var urlsMap: LinkedHashMap<String, String> = LinkedHashMap()
    var title: String? = ""
    var headerMap = HashMap<String, String>()
    var looping = false

    constructor(url: String, title: String?) {
        urlsMap[URL_KEY_DEFAULT] = url
        this.title = title
        currentUrlIndex = 0
    }

    constructor(urlsMap: LinkedHashMap<String, String>?, title: String?) {
        this.urlsMap.clear()
        if (urlsMap != null) {
            this.urlsMap.putAll(urlsMap)
        }
        this.title = title
        currentUrlIndex = 0
    }

    val currentUrl: Any?
        get() = getValueFromLinkedMap(currentUrlIndex)
    val currentKey: Any?
        get() = getKeyFromDataSource(currentUrlIndex)

    fun getKeyFromDataSource(index: Int): String? {
        for ((currentIndex, key) in urlsMap.keys.withIndex()) {
            if (currentIndex == index) {
                return key
            }
        }
        return null
    }

    private fun getValueFromLinkedMap(index: Int): Any? {
        for ((currentIndex, key) in urlsMap.keys.withIndex()) {
            if (currentIndex == index) {
                return urlsMap[key]
            }
        }
        return null
    }

    fun containsTheUrl(info: Any?): Boolean {
        return if (info != null) {
            urlsMap.containsValue(info)
        } else false
    }

    fun cloneMe(): DataSource {
        val map: LinkedHashMap<String, String> = LinkedHashMap()
        map.putAll(urlsMap)
        return DataSource(map, title)
    }

    companion object {
        const val URL_KEY_DEFAULT = "url_key_default"
    }
}