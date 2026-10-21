#include "ddmc_lyrics.hpp"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cctype>

namespace ddmc {

double LyricsEngine::parseTimestamp(const std::string& tag) {
    // Format: mm:ss.xx or mm:ss:xx
    size_t colon = tag.find(':');
    if (colon == std::string::npos) return -1.0;

    std::string minStr = tag.substr(0, colon);
    std::string secStr = tag.substr(colon + 1);

    try {
        double minutes = std::stod(minStr);
        double seconds = std::stod(secStr);
        return minutes * 60.0 + seconds;
    } catch (...) {
        return -1.0;
    }
}

bool LyricsEngine::loadFromFile(const std::string& lrcPath) {
    std::ifstream file(lrcPath);
    if (!file.is_open()) return false;

    m_lines.clear();
    std::string line;

    while (std::getline(file, line)) {
        if (line.empty()) continue;

        // Parse all [mm:ss.xx] tags in this line
        std::vector<double> timestamps;
        size_t pos = 0;

        while ((pos = line.find('[', pos)) != std::string::npos) {
            size_t closePos = line.find(']', pos);
            if (closePos == std::string::npos) break;

            std::string tag = line.substr(pos + 1, closePos - pos - 1);
            double ts = parseTimestamp(tag);
            if (ts >= 0.0) {
                timestamps.push_back(ts);
            }
            pos = closePos + 1;
        }

        if (!timestamps.empty()) {
            std::string text = line.substr(pos);
            // Trim leading/trailing whitespace
            while (!text.empty() && std::isspace(static_cast<unsigned char>(text.front()))) text.erase(text.begin());
            while (!text.empty() && std::isspace(static_cast<unsigned char>(text.back()))) text.pop_back();

            for (double t : timestamps) {
                m_lines.push_back({t, text});
            }
        }
    }

    std::sort(m_lines.begin(), m_lines.end(), [](const LyricLine& a, const LyricLine& b) {
        return a.timestampSec < b.timestampSec;
    });

    return !m_lines.empty();
}

bool LyricsEngine::loadFromAdjacent(const std::string& audioFilePath) {
    size_t dot = audioFilePath.find_last_of('.');
    if (dot == std::string::npos) return false;

    std::string lrcPath = audioFilePath.substr(0, dot) + ".lrc";
    return loadFromFile(lrcPath);
}

void LyricsEngine::clear() {
    m_lines.clear();
}

int LyricsEngine::getActiveIndex(double timeSec) const {
    if (m_lines.empty() || timeSec < m_lines[0].timestampSec) {
        return -1;
    }

    int activeIdx = -1;
    for (size_t i = 0; i < m_lines.size(); ++i) {
        if (m_lines[i].timestampSec <= timeSec) {
            activeIdx = static_cast<int>(i);
        } else {
            break;
        }
    }
    return activeIdx;
}

} // namespace ddmc
