#pragma once
#include "ddmc_types.hpp"
#include <vector>
#include <string>

namespace ddmc {

class LyricsEngine {
public:
    LyricsEngine() = default;
    ~LyricsEngine() = default;

    bool loadFromFile(const std::string& lrcPath);
    bool loadFromAdjacent(const std::string& audioFilePath);
    void clear();

    bool hasLyrics() const { return !m_lines.empty(); }
    size_t getLineCount() const { return m_lines.size(); }

    // Returns index of active lyric line at timestamp, or -1 if none/before start
    int getActiveIndex(double timeSec) const;

    const LyricLine* getLine(size_t index) const {
        if (index < m_lines.size()) return &m_lines[index];
        return nullptr;
    }

    const std::vector<LyricLine>& getLines() const { return m_lines; }

private:
    std::vector<LyricLine> m_lines;
    static double parseTimestamp(const std::string& tag);
};

} // namespace ddmc
