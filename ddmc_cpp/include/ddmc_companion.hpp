#pragma once
#include "ddmc_types.hpp"
#include <string>
#include <vector>

namespace ddmc {

struct CompanionParticle {
    float x = 0.0f;
    float y = 0.0f;
    float vx = 0.0f;
    float vy = 0.0f;
    std::string symbol;
    float life = 1.0f;
};

class CompanionEngine {
public:
    CompanionEngine();
    ~CompanionEngine() = default;

    void setCharacter(DokiCharacter character);
    DokiCharacter getCharacter() const { return m_character; }
    const char* getCharacterName() const;
    const char* getCharacterColor() const;

    void setPerfMode(PerfMode mode);

    // Updates physical simulation and beat reactivity
    void update(float dtSec, float bassEnergy, bool isBeat, int stageWidth, int stageHeight);

    CompanionState getState() const { return m_state; }
    float getX() const { return m_posX; }
    float getY() const { return m_posY; }
    bool isFacingRight() const { return m_facingRight; }

    // Renders the companion frame lines (ASCII art)
    std::vector<std::string> getRenderLines() const;

    const std::vector<CompanionParticle>& getParticles() const { return m_particles; }

    void triggerAction(const std::string& action);

private:
    void spawnParticle(const std::string& symbol, float x, float y);

    DokiCharacter m_character = DOKI_NATSUKI;
    PerfMode m_perfMode = MODE_BALANCED;
    CompanionState m_state = COMPANION_STAND;

    float m_posX = 20.0f;
    float m_posY = 0.0f;
    float m_velX = 0.0f;
    float m_velY = 0.0f;
    bool m_facingRight = true;

    int m_animTick = 0;
    int m_stateTimer = 60;
    float m_beatCooldown = 0.0f;

    std::vector<CompanionParticle> m_particles;
};

} // namespace ddmc
